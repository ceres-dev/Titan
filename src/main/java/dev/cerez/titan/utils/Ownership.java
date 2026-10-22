package dev.cerez.titan.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface Ownership {

    @NotNull
    @Contract(pure = true)
    UUID getIdOwner();
}
