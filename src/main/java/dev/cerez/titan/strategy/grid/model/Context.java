package dev.cerez.titan.strategy.grid.model;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.StatusOrder;
import dev.cerez.titan.strategy.grid.GridManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.util.List;

public record Context(@NotNull BigDecimal balanceUsdt,
                      @NotNull BigDecimal currentPrice,
                      @NotNull GridManager.GridManagerConfig config,
                      @Nullable BinanceConnector.FuturePosition position,
                      @NotNull List<BinanceConnector.FutureOrder> orders
) {

    @Contract(pure = true)
    public @NotNull BigDecimal positionBaseAsset() {
        return position == null
                ? BigDecimal.ZERO
                : position.quantity();
    }

    public static @NotNull @Unmodifiable List<BinanceConnector.FutureOrder> filterFilled(@NotNull List<BinanceConnector.FutureOrder> orders) {
        return orders.stream().filter(o -> o.statusOrder() == StatusOrder.FILLED).toList();
    }

    public static @NotNull @Unmodifiable List<BinanceConnector.FutureOrder> filterNew(@NotNull List<BinanceConnector.FutureOrder> orders) {
        return orders.stream().filter(o -> o.statusOrder() == StatusOrder.NEW).toList();
    }

    public static @NotNull @Unmodifiable List<BinanceConnector.FutureOrder> filterBuy(@NotNull List<BinanceConnector.FutureOrder> orders) {
        return orders.stream().filter(o -> o.sideOrder() == SideOrder.BUY).toList();
    }

    public static @NotNull @Unmodifiable List<BinanceConnector.FutureOrder> filterSell(@NotNull List<BinanceConnector.FutureOrder> orders) {
        return orders.stream().filter(o -> o.sideOrder() == SideOrder.SELL).toList();
    }
}
