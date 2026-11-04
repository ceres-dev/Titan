package dev.cerez.titan.core;

import dev.cerez.titan.TitanApplication;
import dev.cerez.titan.core.event.EventManager;
import dev.cerez.titan.core.event.Listener;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.storage.Storage;
import dev.cerez.titan.storage.StorageProfiler;
import dev.cerez.titan.utils.NoAutoRegister;
import dev.cerez.titan.utils.Revocable;
import dev.cerez.titan.utils.Utils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public abstract class BaseManager<C extends BaseConfig, P extends BasePersistent, O extends Connector> implements Manager<C, P> {

    @NotNull protected final Storage<C> configStorage;
    @NotNull protected final Storage<P> persistenceStorage;
    @NotNull protected final ScheduledExecutorService executor = Executors.newScheduledThreadPool(6, Utils.getThreadFactory(this));
    @Getter @NotNull protected final O connector;
    @Getter @NotNull protected final UUID id = UUID.randomUUID();
    @Getter @Setter @NotNull protected String name = id.toString();
    @Getter @Setter(value = AccessLevel.NONE) protected volatile boolean running = false;
    @Getter protected final TypeManager typeManager;
    @Getter final private EnvironmentManager environmentManager;
    @Getter @Delegate final private EventManager eventManager;

    protected BaseManager(@NotNull StorageProfiler<C, P> storageProfiler, @NotNull O connector, @NotNull TypeManager typeManager) {
        this.configStorage = storageProfiler.config();
        this.persistenceStorage = storageProfiler.persistent();
        this.connector = connector;
        this.typeManager = typeManager;
        {
            if (this instanceof NoAutoRegister) {
                environmentManager = null;
                eventManager = null;
                return;
            }

            UUID uuid = Utils.getUserUUUIDByThread();
            if (uuid != null) {
                EnvironmentManager environment = TitanApplication.getInstance().getEnvironmentManager(uuid);
                if (environment != null) environment.registerManager(this);
                environmentManager = environment;
            }else environmentManager = null;

            if (environmentManager != null) eventManager = environmentManager.getEventManager();
            else eventManager = null;
        }
    }

    @Nullable private volatile C cacheConfig = null;
    @Nullable private volatile P cachePersistence = null;

    public @NotNull C getConfig() {
        if (cacheConfig == null) {
            return cacheConfig = this.configStorage.load();
        }else {
            return Objects.requireNonNull(cacheConfig);
        }
    }

    public P getPersistence() {
        if (cachePersistence == null) {
            return cachePersistence = this.persistenceStorage.load();
        }else {
            return Objects.requireNonNull(cachePersistence);
        }
    }

    @Override
    public void start() {
        if (running)return;
        running = true;
        long currentTime = System.currentTimeMillis();
        Log.info("<cian>Iniciando: %s:%s", this.getClass().getSimpleName(), getName());
        CompletableFuture.runAsync(this::internalStart, executor).join();
        Log.info("<green>Iniciado: %s:%s %.2fs", this.getClass().getSimpleName(), getName(), (System.currentTimeMillis() - currentTime) / 1000d);
    }

    @Override
    public void stop() {
        if (!running)return;
        running = false;
        long currentTime = System.currentTimeMillis();
        Log.info("<cian>Deteniendo: %s:%s", this.getClass().getSimpleName(), getName());
        CompletableFuture.runAsync(this::internalStop, executor).join();
        Log.info("<green>Detenido: %s:%s %.2fs", this.getClass().getSimpleName(), getName(), (System.currentTimeMillis() - currentTime) / 1000d);
    }

    protected abstract void internalStart();

    protected abstract void internalStop();

}
