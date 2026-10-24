package dev.cerez.titan.infrastructure.model;

import java.math.BigDecimal;
import java.util.Map;

public record Balance(Map<String, BigDecimal> spot, Map<String, BigDecimal> future, Map<String, BigDecimal> futureValued, Roi roi) {

    public record Roi(double percent7D, double percent1D, double absolute7D, double absolute1D) {}
}
