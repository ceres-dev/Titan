package dev.cerez.titan.core.strategy;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.PersistenceNope;
import dev.cerez.titan.core.environment.EnvironmentManager;
import dev.cerez.titan.core.strategy.funding.neutral.FundingManager;
import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.core.strategy.triangular.TriangularManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TypeManager {
    GRID(GridManager.class, GridManager.GridManagerConfiguration.class, PersistenceNope.class),
    FUNDING_ON_TIME(FundingOnTimeManager.class, FundingOnTimeManager.FundingMangerConfiguration.class, PersistenceNope.class),
    FUNDING(FundingManager.class, FundingManager.FundingManagerConfiguration.class, FundingManager.FundingManagerPersistan.class),
    TRIANGULAR(TriangularManager.class, TriangularManager.TriangularManagerConfiguration.class, PersistenceNope.class),
    ENVIRONMENT(EnvironmentManager.class, ConfigNope.class, PersistenceNope.class),;

    private final Class<? extends Manager<?>> clazzManager;
    private final Class<? extends BaseConfig> clazzConfig;
    private final Class<?> clazzPersistence;
}
