package dev.cerez.titan.utils;

import dev.cerez.titan.connector.Connector;
import org.jetbrains.annotations.NotNull;

public interface Manager<C> extends Configurable<C>, Switch {

    @NotNull Connector getConnector();

}
