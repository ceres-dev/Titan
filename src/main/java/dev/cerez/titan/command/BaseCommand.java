package dev.cerez.titan.command;

import dev.cerez.titan.core.managers.EnvironmentManager;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
public abstract class BaseCommand {

    protected final String name;
    protected final String alias;
    protected final EnvironmentManager environmentManager;

    public BaseCommand(@NotNull String name, @NotNull String alias, EnvironmentManager environmentManager) {
        this.name = name;
        this.alias = alias;
        this.environmentManager = environmentManager;
    }

    public abstract void execute(@NotNull List<String> args);
}
