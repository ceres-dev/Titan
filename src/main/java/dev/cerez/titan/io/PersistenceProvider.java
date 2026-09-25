package dev.cerez.titan.io;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public interface PersistenceProvider<P> {

    P getPersistence();

    @Contract(pure = true)
    static <P> @NonNull PersistenceProvider<P> from(P p){
        return () -> p;
    }

}