package dev.cerez.titan.utils;

import org.jetbrains.annotations.NotNull;

public interface ProviderSable<P> extends Provider<P> {

    default void save(@NotNull P persistence){};
}
