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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

import static dev.cerez.titan.core.strategy.grid.model.SideGrid.*;

public final class GridManager extends StrategyManager<GridManager.GridManagerConfig, PersistentNope, BinanceConnector> implements StatusProfiler {

    @Nullable private OrderFuture lastOrderFilled = null;
    @NotNull private volatile Future<?> waitForUpdate = CompletableFuture.runAsync(() -> {});

    private final String symbol;
    private final WaitableSet<String> waitForCancel = new WaitableSet<>();
    private final Set<String> forOpen = ConcurrentHashMap.newKeySet();
    private final GridBuilder gridBuilder;
    private final ApplyAttributes applyAttributes;
    private boolean gridProfilerIsConfigured = false;

    public GridManager(@NotNull StorageManager storageManager, @NotNull BinanceConnector connector) {
        super(storageManager.getStorageProfiler(GridManagerConfig.class, PersistentNope.class), connector);
        this.symbol = getConfig().baseAsset + getConfig().quoteAsset;
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
                default -> {
                    return;
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
        waitForCancel.clear();
        forOpen.clear();
        connector.fCancelOrderAll(symbol);
    }

    public synchronized void updateGrid() {
        if (!running) return;
        waitForUpdate.cancel(false);
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
        if (!gridProfilerIsConfigured){
            gridProfilerIsConfigured = true;
            List<OrderPreview> orderPreviews = gridBuilder.buildGrid(context);
            applyAttributes.apply(context, orderPreviews);
        }
        List<OrderPreview> orderPreviews = gridBuilder.buildGrid(context);
        List<OrderPreview> desiredOrders = applyAttributes.apply(context, orderPreviews);
        reconcileOrders(ordersActive, desiredOrders);
    }

    private synchronized void reconcileOrders(@NotNull List<OrderFuture> currentOrders, @NotNull List<OrderPreview> desiredOrders) {
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
        waitForCancel.clear();
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
        forOpen.clear();
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
                Log.info(
                        "Orden enviada %s<reset> @ %.4f qty: %.4f %s reduceOnly=%s",
                        order.getSideOrder() == SideOrder.BUY ? "<green>BUY" : "<red>SELL",
                        order.getPrice(), order.getAmountBaseAsset(), order.getNameOrder(), order.isReduceOnly()
                );
            } catch (PostOnlyRejectException | ReduceOnlyRejectException | MarginNotSufficienException e) {
                Log.warning("Error al crear la orden: " + e.getMessage());
                retry = true;
            }
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
            return stepSizePriority != null ? stepSizePriority : getNormalStepSize();
        }

        /** Devuelve el stepSize configurado para la sesión, ignorando cualquier prioridad temporal. */
        public synchronized BigDecimal getNormalStepSize() {
            if (lastMarketSessionTemporal == null) lastMarketSessionTemporal = new TemporalRefence<>(TimeUnit.MINUTES, 2);
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
        DEFAULT(new Function<>() {
            private boolean estimatingOrders;
            private GridManager gridManager;

            @Override
            public List<BaseAttribute> apply(@NotNull GridManager gridManager) {
                this.gridManager = gridManager;
                return List.of(
                        new CallOnUpdate((order, c) -> {
                            if (c.getConfig().sideGrid == BOTH) throw new UnsupportedOperationException("El modo Both no esta soportado");
                        }),
                        new OffsetOrderSellPrice(new BigDecimal("0.15")),
                        new DontSendOrder(SideAffected.AGAINST, c -> {
                            //noinspection DataFlowIssue
                            return c.getPosition().entryPriceAvg();
                        }, false).addCondicion(c -> c.context().getPosition() != null),
                        new MultiplyFirstOrderSize(SideAffected.AGAINST, (c) -> {
                            // Cantidad APROXIMADA no puede ser que no sea la cantidad real
                            final int amountOrder = gridManager.getAmountOrderApprox(c);
                            switch (c.getConfig().getSideGrid()) {
                                case LONG -> {
                                    return Math.max(amountOrder - Utils.filterBuy(c.getOrders()).size(), 1);
                                }
                                case SHORT -> {
                                    return Math.max(amountOrder - Utils.filterSell(c.getOrders()).size(), 1);
                                }
                                case BOTH -> {
                                    return Math.max(amountOrder - Math.min(Utils.filterSell(c.getOrders()).size(), Utils.filterBuy(c.getOrders()).size()), 1);
                                }
                            }
                            return 1;
                        }),
                        new MultiplyFirstOrderSize(SideAffected.FAVOR, (c) -> {
                            // Cantidad APROXIMADA no puede ser que no sea la cantidad real
                            final int amountOrder = gridManager.getAmountOrderApprox(c);
                            switch (c.getConfig().getSideGrid()) {
                                case LONG -> {
                                    return Math.max((amountOrder / 3) - Utils.filterSell(c.getOrders()).size(), 1);
                                }
                                case SHORT -> {
                                    return Math.max((amountOrder / 3) - Utils.filterBuy(c.getOrders()).size(), 1);
                                }
                            }
                            return 1;
                        }),
                        // Elimina si la orden tiene un precio mayor (En Long)/Menor (En Short) comparado con la última orden ejecutada
                        new RemoveIf((order, c) -> {
                            if (gridManager.lastOrderFilled != null && gridManager.lastOrderFilled.getSideOrder() == order.getSideOrder()) {
                                switch (c.getConfig().getSideGrid()) {
                                    case LONG -> {
                                        if (order.getSideOrder() == SideOrder.BUY) {
                                            return gridManager.lastOrderFilled.getPrice().compareTo(order.getPrice()) <= 0;
                                        } else {
                                            return false;
                                        }
                                    }
                                    case SHORT -> {
                                        if (order.getSideOrder() == SideOrder.SELL) {
                                            return gridManager.lastOrderFilled.getPrice().compareTo(order.getPrice()) >= 0;
                                        } else {
                                            return false;
                                        }
                                    }
                                    case BOTH -> {
                                        return gridManager.lastOrderFilled.getPrice().compareTo(order.getPrice()) == 0;
                                    }
                                }
                            }
                            return false;
                        }).addCondicion(c -> {
                            // Solo se activa cuando hay pocos órdenes de crecimiento
                            long amount = c.ordersResult().stream().filter(o -> !o.isReduceOnly()).count();
                            return amount <= c.context().getConfig().getMinOrderSafe();
                        }),
                        new RemoveIf((order, c) -> {
                            // La orden es la misma que la ultima ejecutada la elimina
                            return gridManager.lastOrderFilled != null &&
                                    gridManager.lastOrderFilled.getSideOrder() == order.getSideOrder() &&
                                    gridManager.lastOrderFilled.getPrice().compareTo(order.getPrice()) == 0;
                        }),
                        new RemoveIf((order, c) -> {
                            // Si la cantidad de órdenes de reduceOnly es mayor que la configurada elimina las órdenes de crecimiento
                            // por encima (Long)/pordebajo (Short) del entryPriceAvg
                            if (c.getOrders().stream().filter(Order::isReduceOnly).count() > c.getConfig().getMinOrderGreed()){
                                return false;
                            }
                            if (!order.isReduceOnly()) {
                                switch (c.getConfig().getSideGrid()) {
                                    case LONG -> {
                                        return (c.getPosition() != null && c.getPosition().entryPriceAvg().compareTo(order.getPrice()) <= 0);
                                    }
                                    case SHORT -> {
                                        return (c.getPosition() != null && c.getPosition().entryPriceAvg().compareTo(order.getPrice()) >= 0);
                                    }
                                    default -> {
                                        return false;
                                    }
                                }
                            }else {
                                return false;
                            }
                        }),
                        new CallOnUpdate((order, c) -> {
                            // La simulación aplica todos los atributos, pero no debe actualizar
                            // el stepSize/status desde esta llamada anidada.
                            if (estimatingOrders) return;

                            GridManagerConfig config = c.getConfig();
                            BigDecimal normalStepSize = config.getNormalStepSize();
                            BigDecimal increasedStepSize = normalStepSize.multiply(BigDecimal.TWO);
                            boolean increasedStepSizeActive = config.getStepSizePriority() != null;

                            // La lista actual ya representa uno de los escenarios y pasó por todos
                            // los atributos. Simulamos únicamente el escenario alternativo.
                            long currentAmount = order.stream().filter(o -> !o.isReduceOnly()).count();
                            long normalAmount = currentAmount;
                            long increasedAmount = currentAmount;
                            boolean estimateSucceeded = true;
                            try {
                                if (increasedStepSizeActive) {
                                    normalAmount = getAmountOrderForStepSize(c, normalStepSize);
                                } else {
                                    increasedAmount = getAmountOrderForStepSize(c, increasedStepSize);
                                }
                            } catch (RuntimeException exception) {
                                // Si falla la simulación, mantenemos el stepSize actual y dejamos
                                // que la actualización normal de órdenes continúe.
                                estimateSucceeded = false;
                                Log.warning("No se pudo estimar la cantidad de órdenes para ajustar StepSize: " + exception.getMessage());
                            }

                            // La decisión siempre se basa en el escenario NORMAL. Así, si el stepSize
                            // ampliado genera más órdenes, ese aumento no provoca que volvamos al normal
                            // en la siguiente actualización.
                            if (estimateSucceeded) {
                                boolean shouldIncreaseStepSize = normalAmount <= config.getMinOrderSafe();
                                if (shouldIncreaseStepSize && !increasedStepSizeActive) {
                                    config.setStepSizePriority(increasedStepSize);
                                    Log.info(
                                            "Aúmento De StepSize=%.4f (órdenes normal=%d, ampliado=%d)",
                                            increasedStepSize, normalAmount, increasedAmount
                                    );
                                } else if (!shouldIncreaseStepSize && increasedStepSizeActive) {
                                    config.setStepSizePriority(null);
                                    Log.info(
                                            "Disminuyendo StepSize=%.4f (órdenes normal=%d, ampliado=%d)",
                                            normalStepSize, normalAmount, increasedAmount
                                    );
                                }
                            }
                            if (order.stream().allMatch(Order::isReduceOnly)) {
                                gridManager.setStatus(StatusManager.INOPERATIVE);
                            } else {
                                gridManager.setStatus(StatusManager.WORKING);

                                if (order.stream().noneMatch(Order::isReduceOnly)) {
                                    gridManager.setStatus(StatusManager.WAITING);
                                } else {
                                    gridManager.setStatus(StatusManager.WORKING);
                                }
                            }
                        })
                );
            }

            private long getAmountOrderForStepSize(@NotNull Context context, @NotNull BigDecimal stepSize) {
                GridManagerConfig config = context.getConfig();
                synchronized (config) {
                    BigDecimal previousStepSize = config.getStepSizePriority();
                    boolean wasEstimatingOrders = estimatingOrders;
                    try {
                        config.setStepSizePriority(stepSize);
                        estimatingOrders = true;

                        // Context nuevo para no mezclar los atributos/cachés de la simulación
                        // con los de la actualización real.
                        Context estimationContext = new Context(
                                context.getBalanceUsdt(),
                                context.getCurrentPrice(),
                                context.getCurrentBookTicker(),
                                config,
                                context.getPosition(),
                                context.getOrders()
                        );
                        List<OrderPreview> previews = gridManager.gridBuilder.buildGrid(estimationContext);
                        return gridManager.applyAttributes.apply(estimationContext, previews).stream()
                                .filter(order -> !order.isReduceOnly())
                                .count();
                    } finally {
                        estimatingOrders = wasEstimatingOrders;
                        config.setStepSizePriority(previousStepSize);
                    }
                }
            }
        });

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