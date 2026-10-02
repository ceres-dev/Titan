package dev.cerez.titan.command;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
public abstract class BaseCommand {

    protected final String name;
    protected final String alias;
    public BaseCommand(@NotNull String name, @NotNull String alias) {
        this.name = name;
        this.alias = alias;
    }

    public abstract void execute(@NotNull List<String> args);
}
