package dev.cerez.titan.utils;

import org.jetbrains.annotations.NotNull;

public interface Nameable {

    @NotNull String getName();

    void setName(@NotNull String name);
}
