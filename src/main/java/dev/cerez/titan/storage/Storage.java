package dev.cerez.titan.storage;

import dev.cerez.titan.utils.Nameable;
import dev.cerez.titan.utils.Provider;
import dev.cerez.titan.utils.ProviderSable;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public interface Storage<O extends Nameable> {

    UUID getIdUser();

    void save(@NotNull O object);

    O load(@NotNull String name);

    @NotNull String getProfilerName();

    void setProfilerName(@NotNull String name);

    default O load(){
        return this.load(getProfilerName());
    }

    default ProviderSable<O> getProvider(){
        return getProvider(getProfilerName());
    }

    default ProviderSable<O> getProvider(String name) {
        Storage<O> storage = this;
        return new ProviderSable<>() {
            @Override
            public O get() {
                return load(name);
            }
            @Override
            public void save(@NotNull O o) {
                storage.save(o);
            }
        };
    }

    default Optional<O> getOptional() {
        return Optional.ofNullable(load(getProfilerName()));
    }

    default Optional<O> getOptional(String name) {
        return Optional.ofNullable(load(name));
    }

    default void save(@NotNull Provider<O> provider) {
        save(provider.get());
    }

    @SuppressWarnings("unchecked")
    default <T extends Nameable> void saveUnsafe(@NotNull T object) {
        save((O) object);
    }

    @SuppressWarnings("unchecked")
    default Provider<O> getProviderOrSave(@NotNull O p){
        Provider<O> loaded = getProvider();
        if(loaded == null){
            save(p);
            return Provider.from(p);
        }else {
            if (loaded.get() == null) {
                save(p);
                return Provider.from(p);
            }else {
                return loaded;
            }
        }
    }

}
