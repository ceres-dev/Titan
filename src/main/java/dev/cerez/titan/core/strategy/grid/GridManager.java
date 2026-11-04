package dev.cerez.titan.core.strategy.grid;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.exception.exchange.MarginNotSufficienException;
import dev.cerez.titan.connector.exception.exchange.PostOnlyRejectException;
import dev.cerez.titan.connector.exception.exchange.ReduceOnlyRejectException;
import dev.cerez.titan.connector.exception.exchange.UnknownOrderException;
import dev.cerez.titan.connector.model.BookTick;
import dev.cerez.titan.connector.model.order.Order;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.StatusOrder;
import dev.cerez.titan.connector.model.order.OrderFuture;
import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.StrategyManager;
import dev.cerez.titan.core.PersistentNope;
import dev.cerez.titan.core.StatusManager;
import dev.cerez.titan.core.strategy.grid.attribute.ApplyAttributes;
import dev.cerez.titan.core.strategy.grid.attribute.BaseAttribute;
import dev.cerez.titan.core.strategy.grid.attribute.SideAffected;
import dev.cerez.titan.core.strategy.grid.attribute.attributes.*;
import dev.cerez.titan.core.strategy.grid.model.Context;
import dev.cerez.titan.connector.model.order.OrderPreview;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
import dev.cerez.titan.discord.StatusProfiler;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.utils.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

public final class GridManager extends StrategyManager<GridManager.GridManagerConfig, PersistentNope, BinanceConnector> implements StatusProfiler {

    @Nullable private OrderFuture lastOrderFilled = null;
    private volatile Future<?> waitForUpdate = CompletableFuture.runAsync(() -> {});

    @NotNull private final String symbol;
    @NotNull private final WaitableSet<String> waitForCancel = new WaitableSet<>();
    @NotNull private final HashSet<String> forOpen = new HashSet<>();
    @NotNull private final PriceAlarm priceAlarm;
    @NotNull private final GridBuilder gridBuilder;
    @NotNull private final ApplyAttributes applyAttributes;

    public GridManager(@NotNull StorageManager storageManager, @NotNull BinanceConnector connector) {
        super(storageManager.createProfilerManager(GridManagerConfig.class, PersistentNope.class), connector);
        this.symbol = getConfig().baseAsset + getConfig().quoteAsset;
        this.priceAlarm = new PriceAlarm(connector, symbol);
        this.gridBuilder = new GridBuilder(getConfig());
        this.applyAttributes = GridProfiler.DEFAULT.getApplyAttributes(this);
    }

    @Override
    public @NotNull StatusProfiler.PresenceProfile getPresenceProfile() {
        BigDecimal balance = connector.fGetBalanceValued().get(getConfig().quoteAsset);
        BigDecimal unPnl = connector.fGetUnPNL().get(getConfig().quoteAsset);
        String label = "Bal: %.2f PNL: %.4f Sy: %s St: %s".formatted(balance, unPnl, symbol, MarketSession.of(getConfig().getBaseAsset()));
        return new PresenceProfile(
                OnlineStatus.ONLINE,
                Activity.of(Activity.ActivityType.PLAYING, label)
        );
    }

    @Override
    public void internalStart() {
        connector.fGetAllSymbols();
        connector.fSetLeverage(symbol, getConfig().leverage);

        Log.info("Balance disponible %.4f %s", fGetBalanceAvailable().get(getConfig().quoteAsset), getConfig().quoteAsset);
        subscribeLive.add(connector.wuSubscribeEventOrderTradeUpdate(orderUpdate -> {
            if (!orderUpdate.symbol().equals(symbol)) {
                return;
            }
            switch (orderUpdate.statusOrder()){
                case CANCELED -> {
                    if (waitForCancel.remove(orderUpdate.name())) return;
                }
                case NEW -> {
                    if (forOpen.remove(orderUpdate.name())) return;
                }
            }
            // Es para que actualize la caché de binance al momentó de obtener los datos
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
            updateGrid();
        }));
        updateGrid();
//        priceAlarm.start();
        executor.execute(() -> {
            while (running) {
                // Para actualizar la gráfica periódicamente para detectar cambios en el precio
                LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(1));
                updateGrid();
            }
        });
    }

    @Override
    public void internalStop() {
//        priceAlarm.stop();
        connector.fCancelOrderAll(symbol);
    }

    public synchronized void updateGrid() {
        if (!running) return;
        waitForUpdate.cancel(false);
        waitForCancel.clear();
        forOpen.clear();
        BigDecimal currentPrice = connector.fGetPrice(symbol);
        CompletableFuture<BinanceConnector.FuturePosition> positionFuture = CompletableFuture.supplyAsync(() -> connector.fGetPosition(symbol));
        CompletableFuture<List<OrderFuture>> ordersFuture = CompletableFuture.supplyAsync(() -> connector.fGetAllOrder(symbol));
        CompletableFuture<BigDecimal> balanceFuture = CompletableFuture.supplyAsync(() -> fGetBalanceValued().getOrDefault(getConfig().quoteAsset, BigDecimal.ZERO));
        CompletableFuture<BookTick> bookTickerFuture = CompletableFuture.supplyAsync(() -> connector.fGetBookTick(symbol));

        GridManagerConfig config = getConfig();

        BinanceConnector.FuturePosition position = positionFuture.join();
        List<OrderFuture> orders = ordersFuture.join();
        BigDecimal balance = balanceFuture.join();
        BookTick bookTicker = bookTickerFuture.join();

        List<OrderFuture> ordersActive = Utils.filterNew(orders);
        lastOrderFilled = orders.stream()
                .filter(orderFuture -> orderFuture.getSymbol().equals(symbol))
                .filter(order -> StatusOrder.FILLED.equals(order.getStatusOrder()))
                .max(Comparator.comparingLong(OrderFuture::getDateFilled))
                .orElse(null);

        BigDecimal balanceUse = position == null
                ? balance
                // En caso de que tenga una posición con PNL negativo se descuenta del margen usable
                : balance.add(position.pnlUnrealize().min(BigDecimal.ZERO)).multiply(new BigDecimal(config.getLeverage()));

        Context context = new Context(balanceUse, currentPrice, bookTicker, config, position, orders);

        List<OrderPreview> orderPreviews = gridBuilder.buildGrid(context);
        List<OrderPreview> desiredOrders = applyAttributes.apply(context, orderPreviews);
        reconcileOrders(ordersActive, desiredOrders);
    }

    private void reconcileOrders(@NotNull List<OrderFuture> currentOrders, @NotNull List<OrderPreview> desiredOrders) {
        Set<String> keptOrders = new HashSet<>();
        List<OrderFuture> ordersToCancel = new ArrayList<>();
        List<OrderPreview> ordersToCreate = new ArrayList<>(desiredOrders);
        for (OrderPreview desired : desiredOrders) {
            OrderFuture matched = null;
            for (OrderFuture current : currentOrders) {
                if (keptOrders.contains(current.getNameOrder())) {
                    continue;
                }
                if (sameOrder(desired, current)) {
                    matched = current;
                    break;
                }
            }

            if (matched != null) {
                keptOrders.add(matched.getNameOrder());
                ordersToCreate.remove(desired);
            }
        }

        // Cancelar orden
        for (OrderFuture current : currentOrders)
            if (!keptOrders.contains(current.getNameOrder())) {
                ordersToCancel.add(current);
            }
        waitForCancel.addAll(ordersToCancel.stream().map(OrderFuture::getNameOrder).toList());
        for (OrderFuture order : ordersToCancel) {
            try {
                connector.fCancelOrder(symbol, order.getNameOrder());
                Log.info("Orden Cancelada: %s", order.getNameOrder());
            } catch (UnknownOrderException uoe) {
                Log.info("La orden ya no existe: %s", order.getNameOrder());
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
        forOpen.addAll(ordersToCreate.stream().map(OrderPreview::getNameOrder).toList());
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
        if (retry && (waitForUpdate.isCancelled() || waitForUpdate.isDone())) {
            waitForUpdate = executor.schedule(this::updateGrid, 5, TimeUnit.SECONDS);
        }
    }

    private boolean sameOrder(@NotNull OrderPreview preview, @NotNull OrderFuture order) {
        return preview.getSideOrder() == order.getSideOrder() &&
                preview.getAmountBaseAsset().compareTo(order.getAmountBaseAsset()) == 0 &&
                preview.getPrice().compareTo(order.getPrice()) == 0 &&
                preview.isReduceOnly() == order.isReduceOnly();
    }

    private int getAmountOrderApprox(@NotNull Context c) {
        BigDecimal amountOrderApproximate = c.getBalanceUsdt()
                .divide(c.getCurrentPrice(), 12, RoundingMode.HALF_EVEN)
                .divide(getConfig().getSizePerOrderBaseAsset(), 12, RoundingMode.HALF_EVEN)
                // Entre dos ya que lo ideal es que tenga la misma cantidad de órdenes de compra y de venta
                .divide(BigDecimal.TWO, 12, RoundingMode.HALF_EVEN)
                .add(BigDecimal.ONE);

        return amountOrderApproximate.intValue();
    }

    @Data
    @SuperBuilder
    @EqualsAndHashCode(callSuper = true)
    public static class GridManagerConfig extends BaseConfig {
        @NotNull private final String baseAsset;
        @NotNull private final String quoteAsset;
        @Getter(AccessLevel.NONE) @NotNull private final BigDecimal stepSizeHighActivity;
        @Getter(AccessLevel.NONE) @NotNull private final BigDecimal stepSizeMediumActivity;
        @Getter(AccessLevel.NONE) @NotNull private final BigDecimal stepSizeLowActivity;
        @NotNull private BigDecimal sizePerOrderBaseAsset;
        @NotNull private SideGrid sideGrid;
        private int minOrderSafe;
        /** Sí hay la cantidad de órdenes es menor al número, agrega órdenes de aúmento en un precio menos favorable */
        private int minOrderGreed;
        private int leverage;

        // No Agregar final, ya que el constructor no es llamado y gson ignorara estos campos por el transient
        @Getter(AccessLevel.NONE) @Builder.Default
        private transient TemporalRefence<MarketSessionState> lastMarketSessionTemporal = null;
        @Getter(AccessLevel.NONE) @Builder.Default
        private transient volatile MarketSessionState lastMarketSession = null;
        @Nullable @Builder.Default
        private transient BigDecimal stepSizePriority = null;


        @Contract
        public synchronized BigDecimal getStepSize() {
            if (lastMarketSessionTemporal == null) lastMarketSessionTemporal = new TemporalRefence<>(TimeUnit.MINUTES, 2);
            if (stepSizePriority != null){
                return stepSizePriority;
            }
            MarketSessionState marketSessionStatus = lastMarketSessionTemporal.getOrCompute(() -> MarketSession.of(baseAsset));
            BigDecimal stepSize = switch (marketSessionStatus) {
                case PRE_MARKET, POST_MARKET -> stepSizeMediumActivity;
                case CLOSED, OVERNIGHT -> stepSizeLowActivity;
                case REGULAR -> stepSizeHighActivity;
            };
            boolean noEquals = !marketSessionStatus.equals(lastMarketSession);
            if (noEquals) {
                Log.info("Cambio de horario %s -> %s StepSize=%.4f", lastMarketSession, marketSessionStatus, stepSize);
                lastMarketSession = marketSessionStatus;
            }
            return stepSize;
        }
    }

    @RequiredArgsConstructor
    @Getter
    public enum GridProfiler{
        DEFAULT((girdManager) -> List.of(
                new OffsetOrderSellPrice(new BigDecimal("0.15")),
                new DontSendOrder(SideAffected.AGAINST, c -> {
                    //noinspection DataFlowIssue
                    return c.getPosition().entryPriceAvg();
                }, false).addCondicion(c -> c.context().getPosition() != null),
                new MultiplyFirstOrderSize(SideAffected.AGAINST, (orders, c) -> {
                    // Cantidad APROXIMADA no puede ser que no sea la cantidad real
                    final int amountOrder = girdManager.getAmountOrderApprox(c);
                    switch(c.getConfig().getSideGrid()) {
                        case LONG -> {
                            return Math.max(amountOrder - Utils.filterBuy(orders).size(), 1);
                        }
                        case SHORT -> {
                            return Math.max(amountOrder - Utils.filterSell(orders).size(), 1);
                        }
                        case BOTH -> {
                            return Math.max(amountOrder - Math.min(Utils.filterSell(orders).size(), Utils.filterBuy(orders).size()), 1);
                        }
                    }
                    return 1;
                }),
                new MultiplyFirstOrderSize(SideAffected.FAVOR, (orders, c) -> {
                    // Cantidad APROXIMADA no puede ser que no sea la cantidad real
                    final int amountOrder = girdManager.getAmountOrderApprox(c);
                    switch(c.getConfig().getSideGrid()) {
                        case LONG -> {
                            return Math.max(amountOrder/3 - Utils.filterSell(orders).size(), 1);
                        }
                        case SHORT -> {
                            return Math.max(amountOrder/3 - Utils.filterBuy(orders).size(), 1);
                        }
                    }
                    return 1;
                }),
                new RemoveIf((order, c) -> {
                    if (girdManager.lastOrderFilled != null && girdManager.lastOrderFilled.getSideOrder() == order.getSideOrder()){
                        switch(c.getConfig().getSideGrid()) {
                            case LONG -> {
                                if (order.getSideOrder() == SideOrder.BUY){
                                    return girdManager.lastOrderFilled.getPrice().compareTo(order.getPrice()) <= 0;
                                }else {
                                    return false;
                                }
                            }
                            case SHORT -> {
                                if (order.getSideOrder() == SideOrder.SELL){
                                    return girdManager.lastOrderFilled.getPrice().compareTo(order.getPrice()) >= 0;
                                }else {
                                    return false;
                                }
                            }
                            case BOTH -> {
                                return girdManager.lastOrderFilled.getPrice().compareTo(order.getPrice()) == 0;
                            }
                        }
                    }
                    return false;
                }).addCondicion(c -> {
                    long amount = c.ordersResult().stream().filter(o -> !o.isReduceOnly()).count();
                    return amount <= c.context().getConfig().getMinOrderSafe();
                }),
                new RemoveIf((order, c) -> {
                    if (girdManager.lastOrderFilled != null && girdManager.lastOrderFilled.getSideOrder() == order.getSideOrder()){
                        return girdManager.lastOrderFilled.getPrice().compareTo(order.getPrice()) == 0;
                    }else {
                        return c.getOrders().stream().filter(Order::isReduceOnly).count() > c.getConfig().getMinOrderGreed() &&
                                !order.isReduceOnly() &&
                                (c.getPosition() != null && c.getPosition().entryPriceAvg().compareTo(order.getPrice()) <= 0);
                    }
                }),
                new CallOnUpdate((order, c) -> {
                    long amount = order.stream().filter(o -> !o.isReduceOnly()).count();
                    GridManagerConfig config = c.getConfig();
                    if (amount > config.getMinOrderSafe()) {
                        if (config.getStepSizePriority() != null) {
                            config.setStepSizePriority(null);
                            Log.info("Disminuyendo StepSize=%.4f", config.getStepSize());
                        }
                    } else {
                        if (config.getStepSizePriority() == null) {
                            BigDecimal stepSize = config.getStepSize().multiply(BigDecimal.TWO);
                            Log.info("Aúmento De StepSize=%.4f", stepSize);
                            config.setStepSizePriority(stepSize);
                        }
                    }
                    if (order.stream().allMatch(Order::isReduceOnly)){
                        girdManager.setStatus(StatusManager.INOPERATIVE);
                    }else {
                        girdManager.setStatus(StatusManager.WORKING);

                        if (order.stream().noneMatch(Order::isReduceOnly)) {
                            girdManager.setStatus(StatusManager.WAITING);
                        }else {
                            girdManager.setStatus(StatusManager.WORKING);
                        }
                    }
                })
        ));

        private final Function<GridManager, List<BaseAttribute>> constructorProfiler;

        @Contract(pure = true, value = "_ -> new")
        public @NotNull ApplyAttributes getApplyAttributes(@NotNull GridManager gridManager) {
            ApplyAttributes attribute = new ApplyAttributes(gridManager.gridBuilder);
            List<BaseAttribute> attributes = constructorProfiler.apply(gridManager);
            attributes.forEach(attribute::add);
            return attribute;
        }
    }
}