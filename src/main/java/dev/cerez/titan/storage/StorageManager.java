package dev.cerez.titan.storage;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.BasePersistent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface StorageManager {

    UUID getIdUser();

    <C extends BaseConfig> Storage<C> getConfig(@NotNull Class<C> clazz);

    <P extends BasePersistent> Storage<P> getPersistent(@NotNull Class<P> clazz);

    <C extends BaseConfig, P extends BasePersistent> StorageProfiler<C, P> createProfilerManager(Class<C> config, Class<P> persistent);

    <C extends BaseConfig> void setConfigProfilerName(Class<C> clazz, String profilerName);

    <P extends BasePersistent> void setPersistentProfilerName(Class<P> clazz, String profilerName);

    default <C extends BaseConfig> C loadConfig(Class<C> clazz) {
        return getConfig(clazz).load();
    }

    default <P extends BasePersistent> P loadPersistence(Class<P> clazz) {
        return getPersistent(clazz).load();
    }

    @Contract(value = "_ -> this")
    default <C extends BaseConfig> @NotNull StorageManager saveConfig(@NotNull C config) {
        getConfig(config.getClass()).saveUnsafe(config);
        return this;
    }

    @Contract(value = "_ -> this")
    default <P extends BasePersistent> @NotNull StorageManager savePersistence(@NotNull P persistent) {
        getPersistent(persistent.getClass()).saveUnsafe(persistent);
        return this;
    }

    @Contract(value = "_ -> this")
    default <C extends BaseConfig> @NotNull StorageManager saveIfNotExits(@NotNull C config) {
        if (loadConfig(config.getClass()) == null) {
            saveConfig(config);
        }
        return this;
    }

    @Contract(value = "_ -> this")
    default <P extends BasePersistent> @NotNull StorageManager saveIfNotExits(@NotNull P persistent) {
        if (loadPersistence(persistent.getClass()) == null) {
            savePersistence(persistent);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    default <C extends BaseConfig> @NotNull C loadOrSaveDefault(@NotNull C config) {
        C c = (C) loadConfig(config.getClass());
        if (c == null) {
            saveConfig(config);
            return config;
        }else {
            return c;
        }
    }

    @SuppressWarnings("unchecked")
    default <P extends BasePersistent> @NotNull P loadOrSaveDefault(@NotNull P persistent) {
        P p = (P) loadPersistence(persistent.getClass());
        if (p == null) {
            savePersistence(persistent);
            return persistent;
        }else {
            return p;
        }
    }
}
