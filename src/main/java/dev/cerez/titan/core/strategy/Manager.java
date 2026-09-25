package dev.cerez.titan.core.strategy;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.utils.Configurable;
import dev.cerez.titan.utils.Identifiable;
import dev.cerez.titan.utils.Nameable;
import dev.cerez.titan.utils.Switch;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface Manager<C> extends Configurable<C>, Switch, Nameable, Identifiable {

    @NotNull Connector getConnector();

    @NotNull UUID getId();

    @NotNull TypeManager getTypeManager();

    void saveConfig();

    void savePersistence();

}
