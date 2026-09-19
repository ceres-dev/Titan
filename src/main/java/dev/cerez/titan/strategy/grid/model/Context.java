package dev.cerez.titan.strategy.grid.model;

import dev.cerez.titan.strategy.grid.GridManager;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record Context(@NotNull BigDecimal balanceUsdt,
                      @NotNull BigDecimal positionBaseAsset,
                      @NotNull BigDecimal currentPrice,
                      @NotNull GridManager.GridManagerConfig config
) {
}
