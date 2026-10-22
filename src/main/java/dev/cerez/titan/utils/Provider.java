package dev.cerez.titan.utils;

import org.jetbrains.annotations.Contract;

public interface Provider<R> {

    @Contract(pure = true)
    R get();

    static <T> Provider<T> from(T object){
        return () -> object;
    }

}
