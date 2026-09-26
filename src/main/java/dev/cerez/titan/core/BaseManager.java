package dev.cerez.titan.core;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.core.event.Listener;
import dev.cerez.titan.core.exception.MangerIsNotRunningException;
import dev.cerez.titan.io.PersistenceProvider;
import dev.cerez.titan.io.StorageManager;
import dev.cerez.titan.io.ConfigurationProvider;
import dev.cerez.titan.core.strategy.Manager;
import dev.cerez.titan.core.event.SupplierEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class BaseManager<C, P, O extends Connector, L extends Listener> implements Manager<C>, SupplierEvent<L> {

    @NotNull private final ConfigurationProvider<C> configProvider;
    @NotNull private final PersistenceProvider<P> persistenceProvider;
    @Getter @NotNull protected final O connector;
    @Getter @NotNull protected final UUID id = UUID.randomUUID();
    @Getter @NotNull protected final StorageManager storageManager;
    @Getter @NotNull protected final Set<L> listeners = new HashSet<>();

    @Getter @Setter @NotNull protected String name = id.toString();

    @Getter @Setter(value = AccessLevel.NONE) protected boolean running = false;

    public BaseManager(@Nullable C configDefault,
                       @NotNull Class<P> persistenceClazz,
                       @NotNull O connector,
                       @NotNull StorageManager storageManager
    ) {
        this.configProvider = ConfigurationProvider.from(configDefault); // storageManager.getProviderOrSaveConfig(configDefault);
        this.persistenceProvider = storageManager.getPersistenceProvider(persistenceClazz);
        this.connector = connector;
        this.storageManager = storageManager;
        this.cacheConfig = configDefault;
    }

    protected void runningOrException(){
        if (!this.running){
            throw new MangerIsNotRunningException();
        }
    }

    public void registerListener(L listener){
        listeners.add(listener);
    }

    protected void callEvent(Consumer<L> consumer){
        listeners.forEach(consumer);
    }

    private final C cacheConfig;
    private P cachePersistence = null;

    public @NotNull C getConfig() {
        return Objects.requireNonNullElse(cacheConfig, this.configProvider.getConfiguration());
    }

    public P getPersistence() {
        return Objects.requireNonNullElse(cachePersistence, cachePersistence = this.persistenceProvider.getPersistence());
    }

    @Override
    public void saveConfig() {
        storageManager.saveConfig(getConfig());
    }

    @Override
    public void savePersistence(){
        storageManager.savePersistence(getConfig());
    }

    protected void saveConfig(C config) {
        storageManager.saveConfig(config);
    }

    protected void savePersistence(P persistence){
        storageManager.savePersistence(persistence);
    }

}
