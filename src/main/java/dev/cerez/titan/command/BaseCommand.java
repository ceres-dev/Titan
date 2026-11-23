package dev.cerez.titan.command;

import dev.cerez.titan.command.exception.CommandUserException;
import dev.cerez.titan.core.managers.EnvironmentManager;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class BaseCommand {

    protected final String name;
    protected final String alias;
    protected final EnvironmentManager environmentManager;

    @Contract(pure = true)
    public BaseCommand(@NotNull String name, @NotNull String alias, EnvironmentManager environmentManager) {
        this.name = name;
        this.alias = alias;
        this.environmentManager = environmentManager;
    }

    protected abstract void execute(@NotNull Arguments args) throws CommandUserException;
}
