package dev.cerez.titan.core;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.core.event.Event;
import dev.cerez.titan.utils.Manager;
import dev.cerez.titan.core.event.SupplierEvent;
import dev.cerez.titan.utils.exception.MangerIsNotRunningException;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public abstract class BaseManager<C, O extends Connector, E extends Event> implements Manager<C>, SupplierEvent<E> {

    @Getter @NotNull protected final C config;
    @Getter @NotNull protected final O connector;
    @Getter @Setter @Nullable protected E event;
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
