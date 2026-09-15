package dev.cerez.titan.connector.model;

public record Volume24H(
        String symbol,
        Double quoteVolumen,
        Double baseVolumen
) {
}
