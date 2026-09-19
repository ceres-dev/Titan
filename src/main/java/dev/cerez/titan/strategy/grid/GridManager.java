package dev.cerez.titan.strategy.grid;

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
import dev.cerez.titan.strategy.grid.attribute.ApplyAttributes;
import dev.cerez.titan.strategy.grid.attribute.attributes.OffsetOrderPrice;
import dev.cerez.titan.strategy.grid.model.Context;
import dev.cerez.titan.strategy.grid.model.OrderPreview;
import dev.cerez.titan.strategy.grid.model.SidePosition;
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
    @NotNull private final WaitableSet<String> waitForCancel = new WaitableSet<>();
    @NotNull private final HashSet<String> forOpen = new HashSet<>();
    @NotNull private final PriceAlarm priceAlarm;
    @NotNull private final GridBuilder gridBuilder;
    @NotNull private final ApplyAttributes applyAttributes;

    public GridManager(GridManagerConfig config) {
        this.config = config;
        this.symbol = config.baseAsset + config.quoteAsset;
        this.priceAlarm = new PriceAlarm(connector, symbol);
        this.gridBuilder = new GridBuilder(config);
        this.applyAttributes = new ApplyAttributes(gridBuilder);
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

        applyAttributes.add(new OffsetOrderPrice(new BigDecimal("0.15")));

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
            while (isStarted) {
                // Para actualizar la gráfica periódicamente para detectar cambios en el precio
                LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(1));
                updateGrid();
            }
        });
    }

    @Override
    public void stop() {
        if (!isStarted) return;
        isStarted = false;
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

        List<BinanceConnector.FutureOrder> ordersActive = orders.stream().filter(order -> StatusOrder.NEW.equals(order.statusOrder())).toList();
        lastOrderFilled = orders.stream().filter(order -> StatusOrder.FILLED.equals(order.statusOrder())).max(Comparator.comparingLong(BinanceConnector.FutureOrder::dateFilled)).orElse(null);

        BigDecimal positionQuantity = position == null
                        ? BigDecimal.ZERO
                        : position.quantity();

        BigDecimal balanceUse = position == null
                ? balance
                // En caso de que tenga una posición con PNL negativo se descuenta del margen usable
                : balance.add(position.pnlUnrealize().min(BigDecimal.ZERO)).multiply(new BigDecimal(config.getLeverage()));

//        List<OrderPreview> desiredOrders = createDesiredOrders(currentPrice, balanceUse, positionQuantity, entryPriceAvg);
        Context context = new Context(balanceUse, positionQuantity, currentPrice, config);


        System.out.println(applyAttributes.apply(context, gridBuilder.buildGrid(context)));
        System.out.println(gridBuilder.buildGrid(context));
        System.exit(0);
//        reconcileOrders(ordersActive, desiredOrders);
    }

    private @NotNull List<OrderPreview> createDesiredOrders(@NotNull BigDecimal currentPrice,
                                                            @NotNull BigDecimal balance,
                                                            @NotNull BigDecimal position,
                                                            @Nullable BigDecimal entryPriceAvg
    ) {
        List<OrderPreview> result = new ArrayList<>();
        CreateOrdersParameter parameter = new CreateOrdersParameter(currentPrice, position, entryPriceAvg);
        SidePosition sidePosition = Utils.toPosition(position);
        switch (config.sideGrid){
            case LONG, SHORT -> {
                SideOrder sideConfig = Utils.toSide(config.sideGrid);

                // Si la posición es inversa a la estrategia. Primero crea las orders de reducción y se obtiene el levelUse
                // para pasárselo a createOpenOrders. En caso contrario primero se crea las órdenes de incremento y luego
                // crea las órdenes de reducción
                boolean isPositionEqualSide = Utils.isEqualSide(sideConfig, sidePosition);
                int levelOffset;
                if (isPositionEqualSide) {
                    levelOffset = 0;
                }else {
                    CreateOrdersResult reduceOrdersResult = createReduceOrders(parameter, null);
                    result.addAll(reduceOrdersResult.orders());
                    levelOffset = reduceOrdersResult.levesUse();
                }

                CreateOrdersResult increaseOrdersRsult = createOpenOrders(parameter, balance, sideConfig, levelOffset);
                result.addAll(increaseOrdersRsult.orders());

                if (isPositionEqualSide) result.addAll(createReduceOrders(parameter, increaseOrdersRsult.amountOrders() - 5).orders() );

            }
            case BOTH -> {
                switch (sidePosition){
                    case LONG, SHORT -> {
                        CreateOrdersParameter parameterWithoutPosicion = new CreateOrdersParameter(currentPrice, BigDecimal.ZERO, entryPriceAvg);
                        CreateOrdersResult increaseOrders = createOpenOrders(parameter, balance, Utils.toSide(sidePosition), 0);
                        CreateOrdersResult reduceOrders = createReduceOrders(parameter, increaseOrders.amountOrders() - 5);
                        CreateOrdersResult inverseOrders = createOpenOrders(parameterWithoutPosicion, balance, Utils.toSide(sidePosition).inverse(), reduceOrders.levesUse());
                        result.addAll(increaseOrders.orders());
                        result.addAll(reduceOrders.orders());
                        result.addAll(inverseOrders.orders());
                    }
                    case NOTHING -> {
                        result.addAll(createOpenOrders(parameter, balance, SideOrder.BUY, 0).orders());
                        result.addAll(createOpenOrders(parameter, balance, SideOrder.SELL, 0).orders());
                    }
                }
            }
        }

        return result;
    }

    private @NotNull CreateOrdersResult createReduceOrders(@NotNull CreateOrdersParameter createOrdersParameter,
                                                           @Nullable Integer timesAmountFirstOrder
    ) {
        BigDecimal currentPrice = createOrdersParameter.currentPrice();
        BigDecimal position = createOrdersParameter.position();
        BigDecimal entryPriceAvg = createOrdersParameter.entryPriceAvg();
        List<OrderPreview> result = new ArrayList<>();
        if (position.signum() == 0) {
            return new CreateOrdersResult(result, 0, 0, null);
        }
        // Si la posición es negativa es un short y requiere órdenes de compra para cerrarla
        boolean isBuy = position.signum() == -1;
        SideOrder side = Utils.toSide(position).inverse();

        BigDecimal AmountPositionToClose;
        if (isBuy) {
            // Si se está creando una orde de cierre en compra eso quiere decir que la posición es un short y por ente la cantidad en negativa
            // No se usa ABS por qué puede ser una posición long dando como resultado posición negativa a la que cerrar
            AmountPositionToClose = position.multiply(new BigDecimal("-1"));
        }else {
            AmountPositionToClose = position;
        }

        int direction = isBuy ? -1 : 1;
        BigDecimal usedMargin = BigDecimal.ZERO;
        Symbol symbols = connector.fGetAllSymbols().get(symbol);
        BigDecimal priceOffset = (isBuy ? BigDecimal.ZERO : symbols.getPriceStepSize()).multiply(new BigDecimal(config.amountPriceOffset));
        priceAlarm.addAlarm(isBuy, gridPrice(
                currentPrice.subtract(priceOffset),
                config.stepSize,
                direction * -1,
                isBuy
        ).add(priceOffset), this::updateGrid);
        boolean isFirstProfit = true;
        int levelUse = 0;
        for (int i = 0; ; i++) {
            levelUse++;
            BigDecimal targetPrice = gridPrice(
                    currentPrice.subtract(priceOffset),
                    config.stepSize,
                    direction * i,
                    isBuy
            ).add(priceOffset);
            // Evitar crear una nueva orden en el mismo precio de se realizó el último filled
            if (lastOrderFilled != null && lastOrderFilled.price().compareTo(targetPrice) == 0) {
                continue;
            }

            if (entryPriceAvg != null) {
                if (isBuy) {
                    if (entryPriceAvg.compareTo(targetPrice) < 0) continue;
                }else {
                    if (entryPriceAvg.compareTo(targetPrice) > 0) continue;
                }
            }
            BigDecimal sizeOrder = config.sizePerOrderBaseAsset.multiply(
                    isFirstProfit && timesAmountFirstOrder != null ?
                            // Crear una orden de cierre más grande, ya que se está quedando sin órdenes de margen
                    new BigDecimal(Math.max(1, timesAmountFirstOrder)) :
                    BigDecimal.ONE
            );
            BigDecimal newUsedMargin = usedMargin.add(sizeOrder);
            if (newUsedMargin.compareTo(AmountPositionToClose) > 0) {
                levelUse--;
                break;
            }
            isFirstProfit = false;
            usedMargin = newUsedMargin;
            result.add(new OrderPreview(targetPrice, side, sizeOrder, true, Utils.uuidToBase36(UUID.randomUUID())));
        }


        return new CreateOrdersResult(result, levelUse, result.size(), side);
    }

    private @NotNull CreateOrdersResult createOpenOrders(@NotNull CreateOrdersParameter createOrdersParameter,
                                                         @NotNull BigDecimal balance,
                                                         @NotNull SideOrder side,
                                                         int offsetLevel
    ) {
        BigDecimal currentPrice = createOrdersParameter.currentPrice();
        BigDecimal position = createOrdersParameter.position();
        BigDecimal entryPriceAvg = createOrdersParameter.entryPriceAvg();
        List<OrderPreview> result = new ArrayList<>();
        BigDecimal availableQuote;
        boolean isBuyOrder = side == SideOrder.BUY;

        if (isBuyOrder) {
            availableQuote = balance.subtract(position.multiply(currentPrice).divide(new BigDecimal(config.leverage), 12 , RoundingMode.HALF_EVEN));
        }else {
            availableQuote = balance.add(position.multiply(currentPrice).divide(new BigDecimal(config.leverage), 12 , RoundingMode.HALF_EVEN));
        }
        BigDecimal leverage = BigDecimal.valueOf(config.leverage);

        int direction = isBuyOrder ? -1 : 1;
        BigDecimal usedMargin = BigDecimal.ZERO;
        Symbol symbols = connector.fGetAllSymbols().get(symbol);
        BigDecimal priceOffset = (isBuyOrder ? BigDecimal.ZERO : symbols.getPriceStepSize()).multiply(new BigDecimal(config.amountPriceOffset));
        priceAlarm.addAlarm(isBuyOrder, gridPrice(
                currentPrice.subtract(priceOffset),
                config.stepSize,
                direction * -1,
                isBuyOrder
        ).add(priceOffset), this::updateGrid);
        int levelUse = 0;
        for (int i = offsetLevel; ; i++) {
            levelUse++;
            BigDecimal targetPrice = gridPrice(
                    currentPrice.subtract(priceOffset),
                    config.stepSize,
                    direction * i,
                    isBuyOrder
            ).add(priceOffset);
            // Si la posición es long, pero intenta enviar una orden de venta debe estar por encima del entryPriceAvg
            if (position.signum() == 1 && !isBuyOrder) {
                if (targetPrice.compareTo(entryPriceAvg) < 0) continue;
            }
            // Si la posición es short, pero intenta enviar una orden de compra debe estar por debajo del entryPriceAvg
            if (position.signum() == -1 && isBuyOrder) {
                if (targetPrice.compareTo(entryPriceAvg) > 0) continue;
            }
            if (lastOrderFilled != null && lastOrderFilled.price().compareTo(targetPrice) == 0 && lastOrderFilled.sideOrder() == side) continue;
            BigDecimal notional = config.sizePerOrderBaseAsset.multiply(targetPrice);
            BigDecimal orderMargin = notional.divide(leverage, 12, RoundingMode.CEILING);
            BigDecimal newUsedMargin = usedMargin.add(orderMargin);
            // dejar un margen del 10%
            if (newUsedMargin.compareTo(availableQuote.multiply(new BigDecimal("0.9"))) > 0) {
                levelUse--;
                break;
            }

            usedMargin = newUsedMargin;
            result.add(new OrderPreview(targetPrice, side, config.sizePerOrderBaseAsset, false, Utils.uuidToBase36(UUID.randomUUID())));
        }

        return new CreateOrdersResult(result, levelUse, result.size(), side);
    }

    private record CreateOrdersResult(@NotNull List<OrderPreview> orders, int levesUse, int amountOrders, SideOrder sideOrder) {}
    //TODO: Hacer una clase builder donde el computerize las ordenes
    private record CreateOrdersParameter(@NotNull BigDecimal currentPrice, @NotNull BigDecimal position, @Nullable BigDecimal entryPriceAvg){}

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


    private static @NotNull BigDecimal gridPrice(@NotNull BigDecimal currentPrice, @NotNull BigDecimal stepSize, int level, boolean isBuy) {
        BigDecimal base = currentPrice.divide(stepSize, 0, isBuy ? RoundingMode.FLOOR : RoundingMode.CEILING).multiply(stepSize);
        return base.add(stepSize.multiply(BigDecimal.valueOf(level)));
    }

    @Builder
    @Data
    public static class GridManagerConfig {
        private final String baseAsset;
        private final String quoteAsset;
        private BigDecimal stepSize;
        private BigDecimal sizePerOrderBaseAsset;
        private SideGrid sideGrid;
        private int leverage;
        private boolean logsEndPoints;
        private int amountPriceOffset;
    }

    public enum SideGrid {
        LONG,
        SHORT,
        BOTH
    }
}