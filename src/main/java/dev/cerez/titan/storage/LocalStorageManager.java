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
public abstract class LocalStorageManager extends BaseStorageManager {

    protected final Map<Class<BaseConfig>, Map<String, Storage<BaseConfig>>> config = new HashMap<>();
    protected final Map<Class<BasePersistent>, Map<String, Storage<BasePersistent>>> persistence = new HashMap<>();
    @Getter
    protected final UUID idUser;

    @Override
    @SuppressWarnings("unchecked")
    public <C extends BaseConfig> Storage<C> getConfig(@NotNull Class<C> clazz, @NotNull String profilerName) {
        return (Storage<C>) config.computeIfAbsent((Class<BaseConfig>) clazz,(c) -> new HashMap<>()).computeIfAbsent(getFullProfilerName(profilerName), (n) -> buildConfig((Class<BaseConfig>) clazz, profilerName));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P extends BasePersistent> Storage<P> getPersistent(@NotNull Class<P> clazz, @NotNull String profilerName) {
        return (Storage<P>) persistence.computeIfAbsent((Class<BasePersistent>) clazz,(c) -> new HashMap<>()).computeIfAbsent(getFullProfilerName(profilerName), (n) -> buildPersistent((Class<BasePersistent>) clazz, profilerName));
    }

    public abstract @NotNull Storage<BaseConfig> buildConfig(@NotNull Class<BaseConfig> clazz, @NotNull String profilerName);

    public abstract @NotNull Storage<BasePersistent> buildPersistent(@NotNull Class<BasePersistent> clazz, @NotNull String profilerName);
}
