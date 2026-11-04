package dev.cerez.titan.connector.model;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record BookTickDouble(
        @NotNull String symbol,
        double bidPrice,
        double bidQty,
        double askPrice,
        double askQty
) {

    @Contract(" -> new")
    public @NotNull BookTick toBigDecimal(){
        return new BookTick(
                symbol,
                BigDecimal.valueOf(bidPrice),
                BigDecimal.valueOf(bidQty),
                BigDecimal.valueOf(askPrice),
                BigDecimal.valueOf(askQty)
        );
    }
}
