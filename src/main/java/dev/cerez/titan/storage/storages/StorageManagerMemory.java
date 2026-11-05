package dev.cerez.titan.storage.storages;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.BasePersistent;
import dev.cerez.titan.storage.BaseStorageManager;
import dev.cerez.titan.storage.LocalStorageManager;
import dev.cerez.titan.storage.Storage;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.utils.Nameable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StorageManagerMemory extends LocalStorageManager implements Serializable {

    public StorageManagerMemory(@NotNull UUID uuid) {
        super(uuid);
    }

    @Override
    public @NotNull Storage<BaseConfig> buildConfig(@NotNull Class<BaseConfig> clazz, @NotNull String profilerName) {
        return new StorageJsonLocal<>(clazz, profilerName);
    }

    @Override
    public @NotNull Storage<BasePersistent> buildPersistent(@NotNull Class<BasePersistent> clazz, @NotNull String profilerName) {
        return new StorageJsonLocal<>(clazz, profilerName);
    }

    @Override
    public BaseStorageManager copy() {
        return new StorageManagerMemory(getIdUser());
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public static class StorageJsonLocal<O extends Nameable> implements Storage<O> {

        @NotNull private final Map<String, O> map = new HashMap<>();
        @NotNull private final Class<O> clazz;
        @NotNull private final String profilerName;

        @Override
        public void save(@NotNull O persistence, @NotNull String name) {
            map.put(name, persistence);
        }

        @Override
        public O load(@NotNull String name) {
            return map.get(name);
        }
    }
}
