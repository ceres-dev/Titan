package dev.cerez.titan.connector.model;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Symbol {

    @NotNull private final String symbol;

    @NotNull @Getter private final Boolean isAllowTrading;
    @NotNull @Getter private final Integer quotePrecision;
    @NotNull @Getter private final String baseAsset;
    @NotNull @Getter private final String quoteAsset;
    @NotNull @Getter private final BigDecimal priceStepSize;
    @NotNull @Getter private final BigDecimal quantityStepSize; // La precision en la compra o venta dela base
    @NotNull @Getter private final BigDecimal minNotionalQuote;
    @NotNull @Getter private final BigDecimal minNotionalBase;


    public Symbol(@NotNull String symbol,
                  @NotNull Integer quotePrecision,
                  @NotNull String baseAsset,
                  @NotNull String quoteAsset,
                  @NotNull Boolean spotTradingAllowed,
                  @NotNull BigDecimal priceStepSize,
                  @NotNull BigDecimal quantityStepSize,
                  @NotNull BigDecimal minNotionalQuote,
                  @NotNull BigDecimal minNotionalBase
    ) {
        this.symbol = symbol;
        this.quotePrecision = quotePrecision;
        this.baseAsset = baseAsset;
        this.quoteAsset = quoteAsset;
        this.isAllowTrading = spotTradingAllowed;
        this.priceStepSize = priceStepSize;
        this.quantityStepSize = quantityStepSize;
        this.minNotionalQuote = minNotionalQuote;
        this.minNotionalBase = minNotionalBase;
    }

    public @NotNull String name() {
        return symbol;
    }

    public Double getStepSizeRaw(){
        return quantityStepSize.doubleValue();
    }

    public double roundPrice(double value) {
        return new BigDecimal(value)
                .divide(priceStepSize, 0, RoundingMode.DOWN)
                .multiply(priceStepSize)
                .doubleValue();
    }

    public double roundQuoteQuantity(double amountQuote) {
        return BigDecimal.valueOf(amountQuote)
                .setScale(quotePrecision, RoundingMode.DOWN)
                .doubleValue();
    }

    public double roundBaseQuantity(double value) {
        return new BigDecimal(value)
                .divide(quantityStepSize, 0, RoundingMode.DOWN)
                .multiply(quantityStepSize)
                .doubleValue();
    }

    public @NotNull BigDecimal roundPrice(@NotNull BigDecimal value) {
        return value
                .divide(priceStepSize, 0, RoundingMode.DOWN)
                .multiply(priceStepSize);
    }

    public @NotNull BigDecimal roundQuoteQuantity(@NotNull BigDecimal amountQuote) {
        return amountQuote
                .setScale(quotePrecision, RoundingMode.DOWN);
    }

    public @NotNull BigDecimal roundBaseQuantity(@NotNull BigDecimal value) {
        return value
                .divide(quantityStepSize, 0, RoundingMode.DOWN)
                .multiply(quantityStepSize);
    }

    private @NotNull BigDecimal roundBaseQuantityUp(@NotNull BigDecimal value) {
        if (quantityStepSize.signum() == 0) {
            return value;
        }

        BigDecimal steps = value.divide(
                quantityStepSize,
                0,
                RoundingMode.CEILING
        );

        return steps.multiply(quantityStepSize);
    }

    public @NotNull BigDecimal realMinNotionalQuote(@NotNull BigDecimal price) {
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        BigDecimal minBaseFromQuote = minNotionalQuote.divide(price, 18, RoundingMode.CEILING);
        BigDecimal realMinBase = minNotionalBase.max(minBaseFromQuote);
        realMinBase = roundBaseQuantityUp(realMinBase);

        return realMinBase.multiply(price).setScale(quotePrecision, RoundingMode.CEILING);
    }

    public @NotNull BigDecimal realMinNotionalBase(@NotNull BigDecimal price) {
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        BigDecimal minBaseFromQuote = minNotionalQuote.divide(price, 18, RoundingMode.CEILING);
        BigDecimal realMinBase = minNotionalBase.max(minBaseFromQuote);

        return roundBaseQuantityUp(realMinBase);
    }

    @Override
    public String toString() {
        return name();
    }

}
