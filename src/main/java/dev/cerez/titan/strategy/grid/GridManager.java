package dev.cerez.titan.strategy.grid;

import com.fasterxml.jackson.databind.JsonNode;
import dev.cerez.titan.Log;
import dev.cerez.titan.Main;
import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.connectors.exception.binance.MarginNotSufficienException;
import dev.cerez.titan.connector.connectors.exception.binance.PostOnlyRejectException;
import dev.cerez.titan.connector.connectors.exception.binance.ReduceOnlyRejectException;
import dev.cerez.titan.connector.connectors.exception.binance.UnknownOrderException;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.StatusOrder;
import dev.cerez.titan.discord.StatusProfiler;
import dev.cerez.titan.strategy.grid.attribute.ApplyAttributes;
import dev.cerez.titan.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.strategy.grid.attribute.attributes.*;
import dev.cerez.titan.strategy.grid.model.Context;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import dev.cerez.titan.strategy.grid.model.SideGrid;
import dev.cerez.titan.utils.BaseManager;
import dev.cerez.titan.utils.WaitableSet;
import lombok.Builder;
import lombok.Data;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class GridManager extends BaseManager<GridManager.GridManagerConfig, BinanceConnector> implements StatusProfiler {

    @Nullable
    private BinanceConnector.FutureOrder lastOrderFilled = null;
    @NotNull private final String symbol;
    @NotNull private final WaitableSet<String> waitForCancel = new WaitableSet<>();
    @NotNull private final HashSet<String> forOpen = new HashSet<>();
    @NotNull private final PriceAlarm priceAlarm;
    @NotNull private final GridBuilder gridBuilder;
    @NotNull private final ApplyAttributes applyAttributes;

    public GridManager(@NotNull GridManagerConfig config, @NotNull BinanceConnector connector) {
        super(config, connector);
        this.symbol = config.baseAsset + config.quoteAsset;
        this.priceAlarm = new PriceAlarm(connector, symbol);
        this.gridBuilder = new GridBuilder(config);
        this.applyAttributes = new ApplyAttributes(gridBuilder);
    }

    @Override
    public @NotNull StatusProfiler.PresenceProfile getPresenceProfile() {
        BigDecimal balance = connector.fGetBalanceTotal().get(config.quoteAsset);
        BigDecimal unPnl = connector.fGetUnPNL().get(config.quoteAsset);
        String label = "Bal: %.2f PNL: %.4f Sy: %s".formatted(balance, unPnl, symbol);
        return new PresenceProfile(
                OnlineStatus.ONLINE,
                Activity.of(Activity.ActivityType.PLAYING, label)
        );
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        Log.info("Iniciando...");
        connector.start();
        connector.getConfig().setLogsRequest(config.logsEndPoints);

        connector.fGetAllSymbols();
        connector.fSetLeverage(symbol, config.leverage);

        applyAttributes.add(new OffsetOrderSellPrice(new BigDecimal("0.15")));
        applyAttributes.add(new DontSendOrder(SideAffected.AGAINST, c -> {
            //noinspection DataFlowIssue
            return c.position().entryPriceAvg();
        }, false).addCondicion(c -> c.context().position() != null));
        applyAttributes.add(new MultiplyFristOrderSize(SideAffected.AGAINST, c -> {
            final int amountOrder = 5;
            List<BinanceConnector.FutureOrder> orders = Context.filterNew(c.orders());
            switch(c.config().getSideGrid()) {
                case LONG -> {
                    return Math.max(Context.filterBuy(orders).size() - amountOrder, 1);
                }
                case SHORT -> {
                    return Math.max(Context.filterSell(orders).size() - amountOrder, 1);
                }
                case BOTH -> {
                    return Math.max(Math.min(Context.filterSell(orders).size(), Context.filterBuy(orders).size()) - amountOrder, 1);
                }
            }
            return 1;
        }));
        applyAttributes.add(new RemoveIf((order, c) ->
                lastOrderFilled != null &&
                lastOrderFilled.price().compareTo(order.getPrice()) == 0 &&
                lastOrderFilled.sideOrder() == order.getSideOrder()
        ));
        applyAttributes.add(new CallOnUpdate((order, c) -> {
            priceAlarm.clear();
            OrderPreview sell = order.stream().filter(OrderPreview::isSell).min(Comparator.comparing(OrderPreview::getPrice)).orElse(null);
            OrderPreview buy = order.stream().filter(OrderPreview::isBuy).max(Comparator.comparing(OrderPreview::getPrice)).orElse(null);
            if (sell != null){
                priceAlarm.addAlarm(false, sell.getPrice().subtract(c.config().getStepSize()), () -> {
                    Log.info("Sell price: %.2f Alarm!!!", c.currentPrice());
                    updateGrid();
                });
            }
            if (buy != null){
                priceAlarm.addAlarm(true, buy.getPrice().add(c.config().getStepSize()), () -> {
                    Log.info("Buy price: %.2f Alarm!!!", c.currentPrice());
                    updateGrid();
                });
            }
        }));

        Log.info("Balance disponible %.4f %s", connector.fGetBalance().get(config.quoteAsset), config.quoteAsset);
        updateGrid();
        connector.wuEventOrderTradeUpdate(payload -> {
            JsonNode node = payload.get("o");
            StatusOrder statusOrder = StatusOrder.parse(node.get("x").asText());
            String nameOrder = node.get("c").asText();
            switch (statusOrder){
                case CANCELED -> {
                    if (waitForCancel.remove(nameOrder)) return;
                }
                case NEW -> {
                    if (forOpen.remove(nameOrder)) return;
                }
            }
            updateGrid();
        }, true);
        Main.executor.execute(() -> {
            while (running) {
                // Para actualizar la gráfica periódicamente para detectar cambios en el precio
                LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(1));
                updateGrid();
            }
        });
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        connector.stop();

        applyAttributes.clear();
        connector.fCloseUserData();
        connector.fCancelOrderAll(symbol);
    }

    public synchronized void updateGrid() {
        waitForCancel.clear();
        forOpen.clear();
        CompletableFuture<BinanceConnector.FuturePosition> positionFuture = CompletableFuture.supplyAsync(() -> connector.fGetPosition(symbol));
        CompletableFuture<BigDecimal> currentPriceFuture = CompletableFuture.supplyAsync(() -> connector.fGetPrice(symbol));
        CompletableFuture<List<BinanceConnector.FutureOrder>> ordersFuture = CompletableFuture.supplyAsync(() -> connector.fGetAllOrder(symbol));
        CompletableFuture<BigDecimal> balanceFuture = CompletableFuture.supplyAsync(() -> connector.fGetBalanceTotal().getOrDefault(config.quoteAsset, BigDecimal.ZERO));

        BinanceConnector.FuturePosition position = positionFuture.join();
        BigDecimal currentPrice = currentPriceFuture.join();
        List<BinanceConnector.FutureOrder> orders = ordersFuture.join();
        BigDecimal balance = balanceFuture.join();

        List<BinanceConnector.FutureOrder> ordersActive = Context.filterNew(orders);
        lastOrderFilled = orders.stream().filter(order -> StatusOrder.FILLED.equals(order.statusOrder())).max(Comparator.comparingLong(BinanceConnector.FutureOrder::dateFilled)).orElse(null);

        BigDecimal balanceUse = position == null
                ? balance
                // En caso de que tenga una posición con PNL negativo se descuenta del margen usable
                : balance.add(position.pnlUnrealize().min(BigDecimal.ZERO)).multiply(new BigDecimal(config.getLeverage()));

        Context context = new Context(balanceUse, currentPrice, config, position, orders);

        List<OrderPreview> orderPreviews = gridBuilder.buildGrid(context);
        List<OrderPreview> desiredOrders = applyAttributes.apply(context, orderPreviews);

        reconcileOrders(ordersActive, desiredOrders);
    }

    private void reconcileOrders(@NotNull List<BinanceConnector.FutureOrder> currentOrders, @NotNull List<OrderPreview> desiredOrders) {
        Set<String> keptOrders = new HashSet<>();
        List<BinanceConnector.FutureOrder> ordersToCancel = new ArrayList<>();
        List<OrderPreview> ordersToCreate = new ArrayList<>(desiredOrders);
        for (OrderPreview desired : desiredOrders) {
            BinanceConnector.FutureOrder matched = null;
            for (BinanceConnector.FutureOrder current : currentOrders) {
                if (keptOrders.contains(current.nameOrder())) {
                    continue;
                }
                if (sameOrder(desired, current)) {
                    matched = current;
                    break;
                }
            }

            if (matched != null) {
                keptOrders.add(matched.nameOrder());
                ordersToCreate.remove(desired);
            }
        }

        // Cancelar orden
        for (BinanceConnector.FutureOrder current : currentOrders)
            if (!keptOrders.contains(current.nameOrder())) {
                ordersToCancel.add(current);
            }
        waitForCancel.addAll(ordersToCancel.stream().map(BinanceConnector.FutureOrder::nameOrder).toList());
        for (BinanceConnector.FutureOrder order : ordersToCancel) {
            try {
                connector.fCancelOrder(symbol, order.nameOrder());
                Log.info("Orden Cancelada: %s", order.nameOrder());
            } catch (UnknownOrderException e) {
                Log.info("La orden ya no existe: %s", order.nameOrder());
            }
        }

        // Se espera para asegurarsé de que las ordenes ya están canceladas
        try {
            waitForCancel.awaitEmpty(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // Enviar orden
        boolean retry = false;
        forOpen.addAll(ordersToCancel.stream().map(BinanceConnector.FutureOrder::nameOrder).toList());
        for (OrderPreview order : ordersToCreate) {
            try {
                connector.fSendOrderToLimit(symbol,
                        order.getSideOrder(),
                        order.getAmountBaseAsset(),
                        order.getNameOrder(),
                        order.getPrice(),
                        order.isReduceOnly()
                );
                // Puede fallar si justo hay movimiento brusco en el precio
                // para evitar eso se vuelve a recalcular en el nuevo precio
            } catch (PostOnlyRejectException | ReduceOnlyRejectException | MarginNotSufficienException e) {
                Log.warning("Error al crear la orden: " + e.getMessage());
                retry = true;
            }

            Log.info(
                    "Orden enviada %s<reset> @ %.4f qty: %.4f %s reduceOnly=%s",
                    order.getSideOrder() == SideOrder.BUY ? "<green>BUY" : "<red>SELL",
                    order.getPrice(), order.getAmountBaseAsset(), order.getNameOrder(), order.isReduceOnly()
            );
        }
        // Se vuelve a intentar
        if (retry) updateGrid();
    }

    private boolean sameOrder(@NotNull OrderPreview preview, @NotNull BinanceConnector.FutureOrder order) {
        return preview.getSideOrder() == order.sideOrder() &&
                preview.getAmountBaseAsset().compareTo(order.amountBaseAsset()) == 0 &&
                preview.getPrice().compareTo(order.price()) == 0 &&
                preview.isReduceOnly() == order.reduceOnly();
    }

    @Builder
    @Data
    public static class GridManagerConfig {
        @NotNull private final String baseAsset;
        @NotNull private final String quoteAsset;
        @NotNull private BigDecimal stepSize;
        @NotNull private BigDecimal sizePerOrderBaseAsset;
        @NotNull private SideGrid sideGrid;
        private int leverage;
        private boolean logsEndPoints;
        private int amountPriceOffset;
    }
}