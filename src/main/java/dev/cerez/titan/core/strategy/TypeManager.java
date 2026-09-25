package dev.cerez.titan.core.strategy;

import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.environment.EnvironmentManager;
import dev.cerez.titan.core.strategy.funding.FundingManager;
import dev.cerez.titan.core.strategy.fundingO.FundingOnTimeManager;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.core.strategy.triangular.TriangularManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TypeManager {
    GRID(GridManager.class, GridManager.GridManagerConfiguration.class),
    FUNDING_ON_TIME(FundingOnTimeManager.class, FundingOnTimeManager.FundingMangerConfiguration.class),
    FUNDING(FundingManager.class, FundingManager.FundingManagerConfiguration.class),
    TRIANGULAR(TriangularManager.class, TriangularManager.TriangularManagerConfiguration.class),
    ENVIRONMENT(EnvironmentManager.class, ConfigNope.class);

    private final Class<? extends Manager<?>> clazzManager;
    private final Class<?> clazzConfig;
}
