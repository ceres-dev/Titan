package dev.cerez.titan.utils;

import dev.cerez.titan.connector.Connector;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public abstract class BaseManager<C, O extends Connector> implements Manager<C> {

    @Getter @NotNull protected final C config;
    @Getter @NotNull protected final O connector;
    @Getter protected boolean running = false;

    public BaseManager(@NotNull C config, @NotNull O connector) {
        this.config = config;
        this.connector = connector;
    }
}
