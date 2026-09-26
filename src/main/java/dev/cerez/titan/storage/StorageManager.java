package dev.cerez.titan.storage;

import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.utils.Provider;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface StorageManager {

    UUID getIdUser();

    <P> void savePersistence(@NotNull P persistence);

    <P> Provider<P> getPersistenceProvider(Class<P> t);

    <C extends BaseConfig> void saveConfig(C configurationProvider);

    <C extends BaseConfig> Provider<C> getConfigProvider(Class<C> t, String nameProfiler);

    default <P> void savePersistence(@NotNull Provider<P> provider) {
        savePersistence(provider.get());
    }

    @SuppressWarnings("unchecked")
    default <P> Provider<P> getProviderOrSavePersistence(@NotNull P p){
        Provider<P> loaded = (Provider<P>) getPersistenceProvider(p.getClass());
        if(loaded == null){
            savePersistence(p);
            return Provider.from(p);
        }else {
            if (loaded.get() == null) {
                savePersistence(p);
                return Provider.from(p);
            }else {
                return loaded;
            }
        }
    }

    default <C extends BaseConfig> void saveConfig(@NotNull Provider<C> provider){
        saveConfig(provider.get());
    }

    @SuppressWarnings("unchecked")
    default <C extends BaseConfig> Provider<C> getProviderOrSaveConfig(C c){
        Provider<C> loaded = (Provider<C>) getConfigProvider(c.getClass(), c.getName());
        if(loaded == null){
            saveConfig(c);
            return Provider.from(c);
        }else {
            if (loaded.get() == null) {
                saveConfig(c);
                return Provider.from(c);
            }else {
                return loaded;
            }
        }
    }
}
