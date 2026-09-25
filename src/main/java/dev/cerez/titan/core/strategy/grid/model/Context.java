package dev.cerez.titan.core.strategy.grid.model;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.strategy.grid.GridManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

public record Context(@NotNull BigDecimal balanceUsdt,
                      @NotNull BigDecimal currentPrice,
                      @NotNull GridManager.GridManagerConfiguration config,
                      @Nullable BinanceConnector.FuturePosition position,
                      @NotNull List<BinanceConnector.OrderFuture> orders
) {

    @Contract(pure = true)
    public @NotNull BigDecimal positionBaseAsset() {
        return position == null
                ? BigDecimal.ZERO
                : position.quantity();
    }
}
