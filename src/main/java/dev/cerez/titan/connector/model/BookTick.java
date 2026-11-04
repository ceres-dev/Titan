package dev.cerez.titan.connector.model;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public record BookTick(
        @NotNull String symbol,
        @NotNull BigDecimal bidPrice,
        @NotNull BigDecimal bidQty,
        @NotNull BigDecimal askPrice,
        @NotNull BigDecimal askQty
){

    @Contract(" -> new")
    public @NotNull BookTickDouble toDouble(){
        return new BookTickDouble(
                symbol,
                bidPrice.doubleValue(),
                bidQty.doubleValue(),
                askPrice.doubleValue(),
                askQty.doubleValue()
        );
    }
}

