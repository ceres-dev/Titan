package dev.cerez.titan.utils;

public interface Status<E extends Enum<?>> {

    E getStatus();
}
