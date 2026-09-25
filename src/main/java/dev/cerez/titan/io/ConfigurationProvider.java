package dev.cerez.titan.io;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public interface ConfigurationProvider<C> {

    C getConfiguration();

    @Contract(pure = true)
    static <C> @NonNull ConfigurationProvider<C> from(C c){
        return () -> c;
    }

}
