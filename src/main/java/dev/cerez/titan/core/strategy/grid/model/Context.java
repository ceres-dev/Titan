package dev.cerez.titan.core.strategy.grid.model;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.strategy.grid.GridManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@Data
public final class Context {
    private final @NotNull BigDecimal balanceUsdt;
    private final @NotNull BigDecimal currentPrice;
    private final BinanceConnector.@NotNull BookTick currentBookTicker;
    private final GridManager.@NotNull GridManagerConfiguration config;
    private final BinanceConnector.@Nullable FuturePosition position;
    private final @NotNull List<BinanceConnector.OrderFuture> orders;
    private final HashMap<String, Object> attributes = new HashMap<>();

    @Contract(pure = true)
    public @NotNull BigDecimal positionBaseAsset() {
        return position == null
                ? BigDecimal.ZERO
                : position.quantity();
    }
}
