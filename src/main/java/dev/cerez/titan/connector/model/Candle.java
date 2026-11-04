package dev.cerez.titan.connector.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record Candle(
        @NotNull String symbol,
        @NotNull BigDecimal closePrice,
        @NotNull BigDecimal openPrice,
        @NotNull BigDecimal highPrice,
        @NotNull BigDecimal lowPrice
) {}
