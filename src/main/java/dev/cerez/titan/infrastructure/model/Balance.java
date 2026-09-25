package dev.cerez.titan.infrastructure.model;

import java.math.BigDecimal;
import java.util.Map;

public record Balance(Map<String, BigDecimal> spot, Map<String, BigDecimal> future) {
}
