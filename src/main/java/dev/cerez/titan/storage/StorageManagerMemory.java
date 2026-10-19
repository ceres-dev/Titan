package dev.cerez.titan.storage;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.BasePersistent;
import dev.cerez.titan.utils.Nameable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StorageManagerMemory extends BaseStorageManager implements Serializable {

    public StorageManagerMemory(UUID uuid) {
        super(uuid);
    }

    @Override
    public @NotNull Storage<BaseConfig> buildConfig(Class<BaseConfig> clazz) {
        return new StorageJsonLocal<>(clazz);
    }

    @Override
    public @NotNull Storage<BasePersistent> buildPersistent(Class<BasePersistent> clazz) {
        return new StorageJsonLocal<>(clazz);
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    public class StorageJsonLocal<O extends Nameable> implements Storage<O> {

        private final Map<String, O> map = new HashMap<>();
        private final UUID idUser = StorageManagerMemory.this.idUser;
        private final Class<O> clazz;
        private String profilerName = "default";

        @Override
        public void save(@NotNull O persistence) {
            map.put(persistence.getName(), persistence);
        }

        @Override
        public O load(@NotNull String name) {
            return map.get(name);
        }
    }
}
