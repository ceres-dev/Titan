package dev.cerez.titan.io;

import dev.cerez.titan.utils.Config;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface StorageManager {

    UUID getId();

    void saveConfig(Config o);

    <T extends Config> T loadConfig(Class<T> t);

    @SuppressWarnings("unchecked")
    default <T extends Config > @NotNull T loadOrSaveConfig(@NotNull T o){
        T loaded = (T) loadConfig(o.getClass());
        if(loaded == null){
            saveConfig(o);
            return o;
        }else {
            return loaded;
        }
    }
}
