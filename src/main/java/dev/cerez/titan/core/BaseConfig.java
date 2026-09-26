package dev.cerez.titan.core;

import dev.cerez.titan.utils.Nameable;
import lombok.Builder;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@SuperBuilder
public class BaseConfig implements Nameable {

    protected BaseConfig(){}

    @Builder.Default
    private String nameProfiler = null;

    @Override
    public @NotNull String getName() {
        return Objects.requireNonNullElse(nameProfiler, "default");
    }

    @Override
    public void setName(@NotNull String name) {
        nameProfiler = name;
    }
}
