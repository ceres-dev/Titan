package dev.cerez.titan.grid;

import com.fasterxml.jackson.databind.JsonNode;
import dev.cerez.titan.Log;
import dev.cerez.titan.Main;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.connectors.exception.binance.MarginNotSufficienException;
import dev.cerez.titan.connector.connectors.exception.binance.PostOnlyRejectException;
import dev.cerez.titan.connector.connectors.exception.binance.ReduceOnlyRejectException;
import dev.cerez.titan.connector.connectors.exception.binance.UnknownOrderException;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.StatusOrder;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.discord.StatusProfiler;
import dev.cerez.titan.utils.Configurable;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import dev.cerez.titan.utils.WaitableSet;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
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
    @Nullable
    private BinanceConnector.FutureOrder lastOrderFilled = null;
    @NotNull
    private final WaitableSet<String> waitForCancel = new WaitableSet<>();
    @NotNull
    private final HashSet<String> forOpen = new HashSet<>();


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
            String nameOrder = node.get("c").asText();
            switch (statusOrder){
                case CANCELED -> {
                    if (waitForCancel.remove(nameOrder)) return;
                }
                case NEW -> {
                    if (forOpen.remove(nameOrder)) return;
                }
                case FILLED -> Main.executor.schedule(this::updateGrid, 5, TimeUnit.SECONDS);
            }
            updateGrid();
        }, true);
//        connector.wfCreateBookTicker(new Consumer<BinanceConnector.BookTick>() {
//            private BigDecimal lastTick = BigDecimal.ZERO;
//
//            @Override
//            public void accept(BinanceConnector.BookTick bookTick) {
//
//            }
//        }, symbol);
        Main.executor.execute(() -> {
            while (isStarted) {
                // Para actualizar la gráfica periódicamente para detectar cambios en el precio
                // TODO: Actualizar cuando el precio cambie cada x porcentaje
                LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(1));
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

        List<BinanceConnector.FutureOrder> ordersActive = orders.stream().filter(order -> StatusOrder.NEW.equals(order.statusOrder())).toList();
        lastOrderFilled = orders.stream().filter(order -> StatusOrder.FILLED.equals(order.statusOrder())).max(Comparator.comparingLong(BinanceConnector.FutureOrder::dateFilled)).orElse(null);

        BigDecimal positionQuantity = position == null
                        ? BigDecimal.ZERO
                        : position.quantity();

        BigDecimal entryPriceAvg = position == null
                ? null
                : position.entryPriceAvg();

        BigDecimal balanceUse = position == null
                ? balance
                // En caso de que tenga una posición con PNL negativo se descuenta del margen usable
                : balance.add(position.pnlUnrealize().min(BigDecimal.ZERO));
        List<OrderPreview> desiredOrders = createDesiredOrders(currentPrice, balanceUse, positionQuantity, entryPriceAvg);
        reconcileOrders(ordersActive, desiredOrders);
    }

    private @NotNull List<OrderPreview> createDesiredOrders(@NotNull BigDecimal currentPrice,
                                                            @NotNull BigDecimal balance,
                                                            @NotNull BigDecimal position,
                                                            @Nullable BigDecimal entryPriceAvg
    ) {
        List<OrderPreview> result = new ArrayList<>();

        if (config.typeGrid == TypeGrid.LONG || config.typeGrid == TypeGrid.BOTH) {
            List<OrderPreview> open = createOpenOrders(currentPrice, position, balance, entryPriceAvg, SideOrder.BUY);
            result.addAll(open);
            result.addAll(createReduceOrders(currentPrice, position, entryPriceAvg, open.size(), SideOrder.SELL));
        }

        if (config.typeGrid == TypeGrid.SHORT || config.typeGrid == TypeGrid.BOTH) {
            List<OrderPreview> open = createOpenOrders(currentPrice, position, balance, entryPriceAvg, SideOrder.SELL);
            result.addAll(open);
            result.addAll(createReduceOrders(currentPrice, position, entryPriceAvg, open.size(), SideOrder.BUY));
        }

        return result;
    }

    private @NotNull List<OrderPreview> createReduceOrders(@NotNull BigDecimal currentPrice,
                                                           @NotNull BigDecimal position,
                                                           @Nullable BigDecimal entryProceAvg,
                                                           int opens,
                                                           @NotNull SideOrder side
    ) {
        List<OrderPreview> result = new ArrayList<>();
        boolean isLong = side == SideOrder.BUY;

        BigDecimal AmountPositionToClose;
        if (isLong) {
            // Si se está creando una orde de cierre en buy eso quiere decir que la posición es un short y por ente la cantidad en negativa
            // No se usa ABS por qué puede ser una posición long dando como resultado posición negativa a la que cerrar
            AmountPositionToClose = position.multiply(new BigDecimal("-1"));
        }else {
            AmountPositionToClose = position;
        }

        int direction = isLong ? -1 : 1;
        BigDecimal usedMargin = BigDecimal.ZERO;
        Symbol symbols = connector.fGetAllSymbols().get(symbol);
        boolean isFirstProfit = true;
        for (int i = 1; ; i++) {
            BigDecimal targetPrice = gridPrice(
                    currentPrice,
                    config.stepSize,
                    direction * i + (isLong ? 1 : 0)
            ).add((isLong ? BigDecimal.ZERO : symbols.getPriceStepSize()).multiply(new BigDecimal(config.amountPriceOffset)));
            // Evitar crear una nueva orden en el mismo precio de se realizó el último filled
            if (lastOrderFilled != null && lastOrderFilled.price().compareTo(targetPrice) == 0) {
                continue;
            }

            if (entryProceAvg != null) {
                if (isLong) {
                    if (entryProceAvg.compareTo(targetPrice) < 0) continue;
                }else {
                    if (entryProceAvg.compareTo(targetPrice) > 0) continue;
                }
            }
            BigDecimal sizeOrder = config.sizePerOrderBaseAsset.multiply(
                    isFirstProfit ?
                            // Crear una orden de cierre más grande, ya que se está quedando sin órdenes de margen
                    new BigDecimal(Math.max(1, 5 - opens)) :
                    new BigDecimal("1")
            );
            BigDecimal newUsedMargin = usedMargin.add(sizeOrder);
            if (newUsedMargin.compareTo(AmountPositionToClose) > 0) {
                break;
            }
            isFirstProfit = false;
            usedMargin = newUsedMargin;
            result.add(new OrderPreview(targetPrice, side, sizeOrder, true, Utils.uuidToBase36(UUID.randomUUID())));
        }

        return result;
    }

    private @NotNull List<OrderPreview> createOpenOrders(@NotNull BigDecimal currentPrice,
                                                         @NotNull BigDecimal position,
                                                         @NotNull BigDecimal balance,
                                                         @NotNull BigDecimal entryPriceAvg,
                                                         @NotNull SideOrder side
    ) {
        List<OrderPreview> result = new ArrayList<>();
        BigDecimal availableQuote;
        boolean isLongOrder = side == SideOrder.BUY;
        if (isLongOrder) {
            availableQuote = balance.subtract(position.multiply(currentPrice).divide(new BigDecimal(config.leverage), 12 , RoundingMode.HALF_EVEN));
        }else {
            availableQuote = balance.add(position.multiply(currentPrice).divide(new BigDecimal(config.leverage), 12 , RoundingMode.HALF_EVEN));
        }
        BigDecimal leverage = BigDecimal.valueOf(config.leverage);

        int direction = isLongOrder ? -1 : 1;
        BigDecimal usedMargin = BigDecimal.ZERO;
        Symbol symbols = connector.fGetAllSymbols().get(symbol);
        for (int i = 1; ; i++) {
            BigDecimal price = gridPrice(
                    currentPrice,
                    config.stepSize,
                    direction * i + (side == SideOrder.BUY ? 1 : 0)
                    // En caso de que sea una orden de venta agrega un offset en el precio
            ).add((isLongOrder ? BigDecimal.ZERO : symbols.getPriceStepSize()).multiply(new BigDecimal(config.amountPriceOffset)));
            // Si la posición es long, pero intenta enviar una orden de venta debe estar por encima del entryPriceAvg
            if (position.signum() == 1 && !isLongOrder) {
                // Si es menor el precio es menor, omitir
                if (price.compareTo(entryPriceAvg) < 0) continue;
            }
            // Si la posición es short, pero intenta enviar una orden de compra debe estar por debajo del entryPriceAvg
            if (position.signum() == -1 && isLongOrder) {
                // Si es mayor el precio es menor, omitir
                if (price.compareTo(entryPriceAvg) > 0) continue;
            }
            if (lastOrderFilled != null && lastOrderFilled.price().compareTo(price) == 0 && lastOrderFilled.sideOrder() == side) continue;
            BigDecimal notional = config.sizePerOrderBaseAsset.multiply(price);
            BigDecimal orderMargin = notional.divide(leverage, 12, RoundingMode.CEILING);
            BigDecimal newUsedMargin = usedMargin.add(orderMargin);
            // dejar un margen del 10%
            if (newUsedMargin.compareTo(availableQuote.multiply(new BigDecimal("0.9"))) > 0) {
                break;
            }
            usedMargin = newUsedMargin;
            result.add(new OrderPreview(price, side, config.sizePerOrderBaseAsset, false, Utils.uuidToBase36(UUID.randomUUID())));
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
        forOpen.addAll(ordersToCancel.stream().map(BinanceConnector.FutureOrder::nameOrder).toList());
        for (OrderPreview order : ordersToCreate) {
            try {
                connector.fSendOrderToLimit(symbol,
                        order.sideOrder(),
                        order.amountBaseAsset(),
                        order.nameOrder(),
                        order.price(),
                        order.reduceOnly()
                );
                // Puede fallar si justo hay movimiento brusco en el precio
                // para evitar eso se vuelve a recalcular en el nuevo precio
            } catch (PostOnlyRejectException | ReduceOnlyRejectException | MarginNotSufficienException e) {
                Log.warning("Error al crear la orden: " + e.getMessage());
                retry = true;
            }

            Log.info(
                    "Orden enviada %s<reset> @ %.4f qty: %.4f %s reduceOnly=%s",
                    order.sideOrder() == SideOrder.BUY ? "<green>BUY" : "<red>SELL",
                    order.price(), order.amountBaseAsset(), order.nameOrder(), order.reduceOnly()
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
            boolean reduceOnly,
            String nameOrder
    ) {}

    @Builder
    @Data
    public static class GridManagerConfig {
        private final String baseAsset;
        private final String quoteAsset;
        private BigDecimal stepSize;
        private BigDecimal sizePerOrderBaseAsset;
        private TypeGrid typeGrid;
        private int leverage;
        private boolean logsEndPoints;
        private int amountPriceOffset;
    }

    public enum TypeGrid {
        LONG,
        SHORT,
        BOTH
    }
}