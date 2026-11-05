package dev.cerez.titan.core;

import dev.cerez.titan.TitanApplication;
import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.core.event.EventManager;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.storage.Storage;
import dev.cerez.titan.storage.StorageProfiler;
import dev.cerez.titan.utils.NoAutoRegister;
import dev.cerez.titan.utils.Utils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public abstract class BaseManager<C extends BaseConfig, P extends BasePersistent, O extends Connector> implements Manager<C, P> {

    protected final Storage<C> configStorage;
    protected final Storage<P> persistenceStorage;
    protected final ScheduledExecutorService executor = Executors.newScheduledThreadPool(6, Utils.getThreadFactory(this));
    @Getter protected final O connector;
    @Getter protected final UUID id = UUID.randomUUID();
    @Getter @Setter protected String name = id.toString();
    @Getter @Setter(value = AccessLevel.NONE) protected volatile boolean running = false;
    @Getter private final EnvironmentManager environmentManager;
    @Getter @Delegate private final EventManager eventManager;

    protected BaseManager(@NotNull StorageProfiler<C, P> storageProfiler, @NotNull O connector) {
        this.configStorage = storageProfiler.config();
        this.persistenceStorage = storageProfiler.persistent();
        this.connector = connector;
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
