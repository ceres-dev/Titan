package dev.cerez.titan.connector.model;

import java.math.BigDecimal;

public record Volume(
        String symbol,
        BigDecimal quoteVolumen,
        BigDecimal baseVolumen
) {
}
