package dev.cerez.tahp.grid;

import com.fasterxml.jackson.databind.JsonNode;
import dev.cerez.tahp.Log;
import dev.cerez.tahp.Main;
import dev.cerez.tahp.connector.connectors.BinanceConnector;
import dev.cerez.tahp.connector.connectors.exception.binance.PostOnlyRejectException;
import dev.cerez.tahp.connector.connectors.exception.binance.UnknownOrderException;
import dev.cerez.tahp.connector.model.SideOrder;
import dev.cerez.tahp.connector.model.StatusOrder;
import dev.cerez.tahp.connector.model.Symbol;
import dev.cerez.tahp.discord.StatusProfiler;
import dev.cerez.tahp.utils.Configurable;
import dev.cerez.tahp.utils.Switch;
import dev.cerez.tahp.utils.Utils;
import dev.cerez.tahp.utils.WaitableSet;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class GridManager implements Switch, StatusProfiler, Configurable<GridManager.GridManagerConfig> {

    private final BinanceConnector connector = new BinanceConnector();
    @Getter
    private final GridManagerConfig config;
    private final String symbol;

    private boolean isStarted = false;
    private volatile boolean onUpdate = false;
    @Nullable
    private BinanceConnector.FutureOrder lastOrderFilled = null;
    @NotNull
    private final WaitableSet<String> waitForCancel = new WaitableSet<>();

    public GridManager(GridManagerConfig config) {
        this.config = config;
        this.symbol = config.baseAsset + config.quoteAsset;

        connector.start();
        connector.getConfig().setLogsRequest(config.logsEndPoints);
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
        if (isStarted) return;
        isStarted = true;
        Log.info("Iniciando...");

        connector.fGetAllSymbols();
        connector.fSetLeverage(symbol, config.leverage);
        connector.initWebSocket(connector.uGetWWS());

        Log.info("Balance disponible %.4f %s", connector.fGetBalance().get(config.quoteAsset), config.quoteAsset);
        updateGrid();
        connector.uEventOrderTradeUpdate(payload -> {
            JsonNode node = payload.get("o");
            StatusOrder statusOrder = StatusOrder.parse(node.get("x").asText());
            if (statusOrder == StatusOrder.CANCELED) {
                if (waitForCancel.remove(node.get("c").asText())) return;
            }

            if (statusOrder == StatusOrder.FILLED){
                LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
            }

            if (!onUpdate) {
                updateGrid();
            }
        }, true);
        Main.executor.execute(() -> {
            while (isStarted) {
                // Para actualizar la gráfica periódicamente para detectar cambios en el precio
                // TODO: Actualizar cuando el precio cambie cada x porcentaje
                LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(3));
                updateGrid();
            }
        });
    }

    @Override
    public void stop() {
        if (!isStarted) return;
        isStarted = false;
        connector.fCloseUserData();
        connector.fCancelOrderAll(symbol);
    }

    public synchronized void updateGrid() {
        if (onUpdate) return;
        onUpdate = true;
        CompletableFuture<BinanceConnector.FuturePosition> positionFuture = CompletableFuture.supplyAsync(() -> connector.fGetPosition(symbol));
        CompletableFuture<BigDecimal> currentPriceFuture = CompletableFuture.supplyAsync(() -> connector.fGetPrice(symbol));
        CompletableFuture<List<BinanceConnector.FutureOrder>> ordersFuture = CompletableFuture.supplyAsync(() -> connector.fGetAllOrder(symbol));
        CompletableFuture<BigDecimal> balanceFuture = CompletableFuture.supplyAsync(() -> connector.fGetBalanceTotal().getOrDefault(config.quoteAsset, BigDecimal.ZERO));

        BinanceConnector.FuturePosition position = positionFuture.join();
        BigDecimal currentPrice = currentPriceFuture.join();
        List<BinanceConnector.FutureOrder> orders = ordersFuture.join();
        BigDecimal balance = balanceFuture.join();

        List<BinanceConnector.FutureOrder> ordersActive = orders.stream().filter(order -> StatusOrder.NEW.equals(order.statusOrder())).toList();
        lastOrderFilled = orders.stream().filter(order -> StatusOrder.FILLED.equals(order.statusOrder())).max(Comparator.comparingLong(BinanceConnector.FutureOrder::dateFilled)).orElse(null);

        BigDecimal positionQuantity = position == null
                        ? BigDecimal.ZERO
                        : position.quantity();

        BigDecimal breakEventPrice = position == null
                ? null
                : position.breakEventPrice();


        List<OrderPreview> desiredOrders = createDesiredOrders(currentPrice, balance, positionQuantity, breakEventPrice);
        reconcileOrders(ordersActive, desiredOrders);
        onUpdate = false;
    }

    private @NotNull List<OrderPreview> createDesiredOrders(@NotNull BigDecimal currentPrice,
                                                            @NotNull BigDecimal balance,
                                                            @NotNull BigDecimal position,
                                                            @Nullable BigDecimal breakEventPrice
    ) {
        List<OrderPreview> result = new ArrayList<>();

        if (config.typeGrid == TypeGrid.LONG || config.typeGrid == TypeGrid.BOTH) {
            result.addAll(createReduceOrders(currentPrice, position, breakEventPrice, SideOrder.SELL));
            result.addAll(createOpenOrders(currentPrice, position, balance, SideOrder.BUY));
        }

        if (config.typeGrid == TypeGrid.SHORT || config.typeGrid == TypeGrid.BOTH) {
            result.addAll(createReduceOrders(currentPrice, position, breakEventPrice, SideOrder.BUY));
            result.addAll(createOpenOrders(currentPrice, position, balance, SideOrder.SELL));
        }

        return result;
    }

    private @NotNull List<OrderPreview> createReduceOrders(@NotNull BigDecimal currentPrice, @NotNull BigDecimal position, @Nullable BigDecimal breakEventPrice, @NotNull SideOrder side) {
        List<OrderPreview> result = new ArrayList<>();
        BigDecimal AmountPositionToClose;
        if (SideOrder.BUY.equals(side)) {
            // Si se está creando una orde de cierre en buy eso quiere decir que la posición es un short y por ente la cantidad en negativa
            // No se usa ABS por qué puede ser una posición long dando como resultado posición negativa a la que cerrar
            AmountPositionToClose = position.multiply(new BigDecimal("-1"));
        }else {
            AmountPositionToClose = position;
        }

        boolean isLong = side == SideOrder.BUY;
        int direction = isLong ? -1 : 1;
        BigDecimal usedMargin = BigDecimal.ZERO;
        Symbol symbols = connector.fGetAllSymbols().get(symbol);
        for (int i = 1; /*i <= amountOrders*/; i++) {
            BigDecimal targetPrice = gridPrice(
                    currentPrice,
                    config.stepSize,
                    direction * i + (isLong ? 1 : 0)
            ).add((isLong ? BigDecimal.ZERO : symbols.getPriceStepSize()).multiply(new BigDecimal(config.amountPriceOffset)));
            // Evitar crear una nueva orden en el mismo precio de se realizó el último filled
            if (lastOrderFilled != null && lastOrderFilled.price().compareTo(targetPrice) == 0) {
                continue;
            }

            if (breakEventPrice != null) {
                if (isLong) {
                    if (breakEventPrice.compareTo(targetPrice) < 0) continue;
                }else {
                    if (breakEventPrice.compareTo(targetPrice) > 0) continue;
                }
            }

            BigDecimal newUsedMargin = usedMargin.add(config.sizePerOrderBaseAsset);
            if (newUsedMargin.compareTo(AmountPositionToClose) > 0) {
                break;
            }
            usedMargin = newUsedMargin;
            result.add(new OrderPreview(targetPrice, side, config.sizePerOrderBaseAsset, true));
        }

        return result;
    }

    private @NotNull List<OrderPreview> createOpenOrders(@NotNull BigDecimal currentPrice, @NotNull BigDecimal position, @NotNull BigDecimal balance, @NotNull SideOrder side) {
        List<OrderPreview> result = new ArrayList<>();
        BigDecimal availableQuote;
        boolean isLong = side == SideOrder.BUY;
        if (isLong) {
            availableQuote = balance.subtract(position.multiply(currentPrice).divide(new BigDecimal(config.leverage), 12 , RoundingMode.HALF_EVEN));
        }else {
            availableQuote = balance.add(position.multiply(currentPrice).divide(new BigDecimal(config.leverage), 12 , RoundingMode.HALF_EVEN));
        }
        BigDecimal leverage = BigDecimal.valueOf(config.leverage);

        int direction = isLong ? -1 : 1;
        BigDecimal usedMargin = BigDecimal.ZERO;
        Symbol symbols = connector.fGetAllSymbols().get(symbol);
        for (int i = 1; ; i++) {
            BigDecimal price = gridPrice(
                    currentPrice,
                    config.stepSize,
                    direction * i + (side == SideOrder.BUY ? 1 : 0)
            ).add((isLong ? BigDecimal.ZERO : symbols.getPriceStepSize()).multiply(new BigDecimal(config.amountPriceOffset)));
            if (lastOrderFilled != null && lastOrderFilled.price().compareTo(price) == 0 && lastOrderFilled.sideOrder() == side) continue;
            BigDecimal notional = config.sizePerOrderBaseAsset.multiply(price);
            BigDecimal orderMargin = notional.divide(leverage, 12, RoundingMode.CEILING);
            BigDecimal newUsedMargin = usedMargin.add(orderMargin);
            // dejar un margen del 10%
            if (newUsedMargin.compareTo(availableQuote.multiply(new BigDecimal("0.9"))) > 0) {
                break;
            }
            usedMargin = newUsedMargin;

            result.add(new OrderPreview(price, side, config.sizePerOrderBaseAsset, false));
        }

        return result;
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

        // Asegurarsé que las ordenes ya están canceladas
        try {
            waitForCancel.awaitEmpty(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // Enviar orden
        boolean retry = false;
        for (OrderPreview order : ordersToCreate) {
            String clientOrderId = Utils.uuidToBase36(UUID.randomUUID());
            try {
                connector.fSendOrderToLimit(symbol,
                        order.sideOrder(),
                        order.amountBaseAsset(),
                        clientOrderId,
                        order.price(),
                        order.reduceOnly()
                );
                // Puede fallar si justo hay movimiento brusco en el precio
                // para evitar eso se vuelve a recalcular en el nuevo precio
            } catch (PostOnlyRejectException e) {
                retry = true;
            }

            Log.info(
                    "Orden enviada %s<reset> @ %.4f qty: %.4f %s reduceOnly=%s",
                    order.sideOrder() == SideOrder.BUY ? "<green>BUY" : "<red>SELL",
                    order.price(), order.amountBaseAsset(), clientOrderId, order.reduceOnly()
            );
        }
        // Se vuelve a intentar
        if (retry) updateGrid();
    }

    private boolean sameOrder(@NotNull OrderPreview preview, @NotNull BinanceConnector.FutureOrder order) {
        return preview.sideOrder() == order.sideOrder() &&
                preview.amountBaseAsset().compareTo(order.amountBaseAsset()) == 0 &&
                preview.price().compareTo(order.price()) == 0 &&
                preview.reduceOnly() == order.reduceOnly();
    }


    private static @NotNull BigDecimal gridPrice(@NotNull BigDecimal currentPrice, @NotNull BigDecimal stepSize,int level) {
        BigDecimal base = currentPrice.divide(stepSize, 0, RoundingMode.FLOOR).multiply(stepSize);
        return base.add(stepSize.multiply( BigDecimal.valueOf(level)));
    }

    private record OrderPreview(
            BigDecimal price,
            SideOrder sideOrder,
            BigDecimal amountBaseAsset,
            boolean reduceOnly
    ) {}

    @Builder
    @Setter
    public static class GridManagerConfig {
        private String baseAsset;
        private String quoteAsset;
        private BigDecimal stepSize;
        private BigDecimal sizePerOrderBaseAsset;
        private TypeGrid typeGrid;
        private int leverage;
        private boolean logsEndPoints;
        private int amountPriceOffset;
        @Builder.Default
        private int amountLevesExtraInverse = 0;
    }

    public enum TypeGrid {
        LONG,
        SHORT,
        BOTH
    }
}