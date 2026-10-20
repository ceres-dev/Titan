package dev.cerez.titan.storage;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.BasePersistent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public abstract class BaseStorageManager implements StorageManager {

    protected final Map<Class<BaseConfig>, Storage<BaseConfig>> config = new HashMap<>();
    protected final Map<Class<BasePersistent>, Storage<BasePersistent>> persistence = new HashMap<>();
    @Getter
    protected final UUID idUser;

    @Override
    @SuppressWarnings("unchecked")
    public <C extends BaseConfig> Storage<C> getConfig(@NotNull Class<C> clazz) {
        return (Storage<C>) config.computeIfAbsent((Class<BaseConfig>) clazz, this::buildConfig);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P extends BasePersistent> Storage<P> getPersistent(@NotNull Class<P> clazz) {
        return (Storage<P>) persistence.computeIfAbsent((Class<BasePersistent>) clazz, this::buildPersistent);
    }

    @Override
    public  <C extends BaseConfig, P extends BasePersistent> StorageProfiler<C, P> createProfilerManager(Class<C> config, Class<P> persistent){
        return new StorageProfiler<>(getConfig(config), getPersistent(persistent));
    }

    @Override
    public <C extends BaseConfig> void setConfigProfilerName(Class<C> clazz, String profilerName){
        getConfig(clazz).setProfilerName(profilerName);
    }

    @Override
    public <P extends BasePersistent> void setPersistentProfilerName(Class<P> clazz, String profilerName){
        getPersistent(clazz).setProfilerName(profilerName);
    }

    public abstract @NotNull Storage<BaseConfig> buildConfig(Class<BaseConfig> clazz);

    public abstract @NotNull Storage<BasePersistent> buildPersistent(Class<BasePersistent> clazz);
}
