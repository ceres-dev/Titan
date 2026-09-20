package dev.cerez.titan.utils;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.utils.exception.MangerIsNotRunningException;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public abstract class BaseManager<C, O extends Connector> implements Manager<C> {

    @Getter @NotNull protected final C config;
    @Getter @NotNull protected final O connector;
    @Getter protected boolean running = false;
    @Getter protected UUID id = UUID.randomUUID();

    public BaseManager(@NotNull C config, @NotNull O connector) {
        this.config = config;
        this.connector = connector;
    }

    protected void runningOrException(){
        if (!this.running){
            throw new MangerIsNotRunningException("Manger is not running");
        }
    }
}
