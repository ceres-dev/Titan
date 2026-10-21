package dev.cerez.titan.core.strategy;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.Manager;
import dev.cerez.titan.core.PersistentNope;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.core.managers.BalanceMaxRiskManager;
import dev.cerez.titan.core.managers.WalletsManager;
import dev.cerez.titan.core.strategy.funding.neutral.FundingNeutralManager;
import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.core.strategy.triangular.TriangularManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TypeManager {
    GRID(GridManager.class, GridManager.GridManagerConfig.class, PersistentNope.class),
    FUNDING_ON_TIME(FundingOnTimeManager.class, FundingOnTimeManager.FundingManagerConfig.class, PersistentNope.class),
    FUNDING_NEUTRAL(FundingNeutralManager.class, FundingNeutralManager.FundingManagerConfiguration.class, FundingNeutralManager.FundingManagerPersistan.class),
    FUNDING_TRIANGULAR(TriangularManager.class, TriangularManager.TriangularManagerConfiguration.class, PersistentNope.class),
    ENVIRONMENT(EnvironmentManager.class, ConfigNope.class, PersistentNope.class),
    BALANCE_MAX_RISK(BalanceMaxRiskManager.class, ConfigNope.class, PersistentNope.class),
    WALLETS(WalletsManager.class, ConfigNope.class, PersistentNope.class),;

    private final Class<? extends Manager<?, ?>> clazzManager;
    private final Class<? extends BaseConfig> clazzConfig;
    private final Class<?> clazzPersistence;
}
