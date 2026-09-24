package dev.cerez.titan.utils;

import dev.cerez.titan.connector.Connector;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface Manager<C extends Config> extends Configurable<C>, Switch, Nameable, Identifiable {

    @NotNull Connector getConnector();

    @NotNull UUID getId();
}
