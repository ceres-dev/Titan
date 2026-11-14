package dev.cerez.titan.storage;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.BasePersistent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;
import java.util.UUID;
// TODO: renombrar Profiler -> Path es un nombre más adecuado para lo que hace
public interface StorageManager {

    String DEFAULT_PROFILER_NAME = "default";
    String SYMBOL_SEPARATOR = "-";

    UUID getIdUser();

    <C extends BaseConfig> Storage<C> getConfig(@NotNull Class<C> clazz, @NotNull String profilerName);

    <P extends BasePersistent> Storage<P> getPersistent(@NotNull Class<P> clazz, @NotNull String profilerName);

    @Contract(pure = true, value = "_ -> new")
    @NotNull StorageManager createSubProfiler(@NotNull String subProfilerName);

    @Contract(pure = true)
    @NotNull StorageManager getFatherProfiler();

    @Contract(pure = true)
    @NotNull @Unmodifiable
    Set<StorageManager> getChildrenProfiler();

    default <C extends BaseConfig> Storage<C> getConfig(@NotNull Class<C> clazz){
        return getConfig(clazz, getDefaultProfilerName());
    }

    default <P extends BasePersistent> Storage<P> getPersistent(@NotNull Class<P> clazz){
        return getPersistent(clazz, getDefaultProfilerName());
    }

    default <C extends BaseConfig> C loadConfig(Class<C> clazz) {
        return getConfig(clazz).load();
    }

    default <P extends BasePersistent> P loadPersistence(Class<P> clazz) {
        return getPersistent(clazz).load();
    }

    @Contract(value = "_ -> this")
    default <C extends BaseConfig> @NotNull StorageManager saveConfig(@NotNull C config) {
        return saveConfig(config, getDefaultProfilerName());
    }

    @Contract(value = "_, _-> this")
    default <C extends BaseConfig> @NotNull StorageManager saveConfig(@NotNull C config, @NotNull String profilerName) {
        getConfig(config.getClass(), profilerName).saveUnsafe(config);
        return this;
    }

    @Contract(value = "_ -> this")
    default <P extends BasePersistent> @NotNull StorageManager savePersistence(@NotNull P persistent) {
        return savePersistence(persistent, getDefaultProfilerName());
    }

    @Contract(value = "_, _ -> this")
    default <P extends BasePersistent> @NotNull StorageManager savePersistence(@NotNull P persistent, @NotNull String profilerName) {
        getPersistent(persistent.getClass(), profilerName).saveUnsafe(persistent);
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

    default <C extends BaseConfig, P extends BasePersistent> StorageProfiler<C, P> getStorageProfiler(Class<C> config, Class<P> persistent){
        return getStorageProfiler(config, persistent, getDefaultProfilerName());
    }

    default <C extends BaseConfig, P extends BasePersistent> StorageProfiler<C, P> getStorageProfiler(Class<C> config, Class<P> persistent, String nameProfiler){
        return new StorageProfiler<>(getConfig(config, nameProfiler), getPersistent(persistent, nameProfiler));
    }

    default String getDefaultProfilerName(){
        return DEFAULT_PROFILER_NAME;
    }

    default String getBaseNameProfiler(){
        return "";
    }

    default String getFullProfilerName(String profilerName){
        String s = getBaseNameProfiler();
        if (s.isEmpty()){
            return profilerName;
        }else {
            return s.concat(SYMBOL_SEPARATOR).concat(profilerName);
        }
    }
}
