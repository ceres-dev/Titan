package dev.cerez.titan.utils;

public interface Provider<R> {

    R get();

    static <T> Provider<T> from(T object){
        return () -> object;
    }

}
