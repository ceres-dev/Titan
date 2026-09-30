package dev.cerez.titan.core.strategy;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class BalanceRiskManager {

    private final HashMap<String, BigDecimal> balanceRisk = new HashMap<>();
    private final BinanceConnector connector;

    public BalanceRiskManager(BinanceConnector connector, Map<String, BigDecimal> balance) {
        balanceRisk.putAll(normalize(balance));
        this.connector = connector;
    }

    public Map<String, BigDecimal> futuroBalance(Manager<?> manager) {
        Map<String, BigDecimal> result = new HashMap<>();
        connector.fGetBalance().forEach((s, amount) -> result.put(s, amount.multiply(balanceRisk.getOrDefault(manager.getName(), BigDecimal.ZERO))));
        return result;
    }

    public Map<String, BigDecimal> futuroBalanceTotal(Manager<?> manager) {
        Map<String, BigDecimal> result = new HashMap<>();
        connector.fGetBalanceTotal().forEach((s, amount) -> result.put(s, amount.multiply(balanceRisk.getOrDefault(manager.getName(), BigDecimal.ZERO))));
        return result;
    }

    public Map<String, BigDecimal> spotBalance(Manager<?> manager) {
        Map<String, BigDecimal> result = new HashMap<>();
        connector.sGetBalance().forEach((s, amount) -> result.put(s, amount.multiply(balanceRisk.getOrDefault(manager.getName(), BigDecimal.ZERO))));
        return result;
    }

    private static <K> Map<K, BigDecimal> normalize(@NotNull Map<K, BigDecimal> values) {
        BigDecimal total = values.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("La suma de los valores no puede ser 0");
        }

        return values.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().divide(
                        total,
                        12,
                        RoundingMode.FLOOR
                )
        ));
    }

}
