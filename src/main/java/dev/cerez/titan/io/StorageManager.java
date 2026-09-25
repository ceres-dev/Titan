package dev.cerez.titan.io;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface StorageManager {

    UUID getIdUser();

    <P> void savePersistence(@NotNull P persistence);

    <P> PersistenceProvider<P> getPersistenceProvider(Class<P> t);

    default <P> void savePersistence(@NotNull PersistenceProvider<P> persistenceProvider){
        saveConfig(persistenceProvider.getPersistence());
    }

    @SuppressWarnings("unchecked")
    default <P> PersistenceProvider<P> getProviderOrSavePersistence(@NotNull P p){
        PersistenceProvider<P> loaded = (PersistenceProvider<P>) getPersistenceProvider(p.getClass());
        if(loaded == null){
            savePersistence(p);
            return PersistenceProvider.from(p);
        }else {
            return loaded;
        }
    }

    <C> void saveConfig(C configurationProvider);

    <C> ConfigurationProvider<C> getConfigProvider(Class<C> t);

    default <C> void saveConfig(@NotNull ConfigurationProvider<C> configurationProvider){
        saveConfig(configurationProvider.getConfiguration());
    }

    @SuppressWarnings("unchecked")
    default <C> ConfigurationProvider<C> getProviderOrSaveConfig(C c){
        ConfigurationProvider<C> loaded = (ConfigurationProvider<C>) getConfigProvider(c.getClass());
        if(loaded == null){
            saveConfig(c);
            return ConfigurationProvider.from(c);
        }else {
            return loaded;
        }
    }
}
