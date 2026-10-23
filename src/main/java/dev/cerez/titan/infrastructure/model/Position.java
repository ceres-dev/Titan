package dev.cerez.titan.infrastructure.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record Position(@NotNull String baseAsset,
                       @NotNull String quoteAsset,
                       @NotNull BigDecimal quantity,
                       @NotNull BigDecimal entryPriceAvg,
                       @NotNull BigDecimal pnlUnrealize,
                       @NotNull BigDecimal nextComision) {
}
