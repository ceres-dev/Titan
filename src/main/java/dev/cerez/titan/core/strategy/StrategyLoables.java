package dev.cerez.titan.core.strategy;

import dev.cerez.titan.core.StrategyManager;
import dev.cerez.titan.core.strategy.financing.timely.TimelyFinancingManager;
import dev.cerez.titan.core.strategy.grid.GridManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum StrategyLoables {
    GRID(GridManager.class),
    FINANCING(TimelyFinancingManager.class);

    private final Class<? extends StrategyManager<?, ?, ?>> manager;
}
