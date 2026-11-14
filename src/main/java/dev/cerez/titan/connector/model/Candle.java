package dev.cerez.titan.connector.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record Candle(
        @NotNull String symbol,
        @NotNull BigDecimal openPrice,
        @NotNull BigDecimal highPrice,
        @NotNull BigDecimal lowPrice,
        @NotNull BigDecimal closePrice,
        @NotNull Volume volume,
        boolean isClosed,
        long openTime,
        long closeTime
) {}
