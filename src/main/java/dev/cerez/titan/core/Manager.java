package dev.cerez.titan.core;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.utils.Configurable;
import dev.cerez.titan.utils.Identifiable;
import dev.cerez.titan.utils.Nameable;
import dev.cerez.titan.utils.Switch;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface Manager<C extends BaseConfig, P extends BasePersistent> extends Configurable<C>, Switch, Nameable, Identifiable {

    @NotNull Connector getConnector();

    @NotNull UUID getId();

    @NotNull TypeManager getTypeManager();

    void registerEnvironment(@NotNull EnvironmentManager environment);

    @Contract(pure = true)
    EnvironmentManager getEnvironmentManager();

    void setEnvironmentManager(@NotNull EnvironmentManager environmentManager);
}
