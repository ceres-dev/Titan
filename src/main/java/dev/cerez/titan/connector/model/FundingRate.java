package dev.cerez.titan.connector.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record FundingRate(String symbol, FundingConfig config, BigDecimal nextFundingRate, long nextFundingTime) {
    public @NotNull BigDecimal rate24h(){
        return nextFundingRate.multiply(BigDecimal.valueOf(24d / config.interval()));
    }

    public @NotNull BigDecimal rate24hAbs(){
        return rate24h().abs();
    }

    public @NotNull BigDecimal nextFundingRateAbs() {
        return nextFundingRate.abs();
    }
}