package dev.cerez.titan.command.commands;

import dev.cerez.titan.TitanApplication;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.core.managers.EnvironmentManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExitCommand extends BaseCommand {

    public ExitCommand(EnvironmentManager environmentManager) {
        super("exit", "x", environmentManager);
    }

    @Override
    public void execute(@NotNull List<String> args) {
        TitanApplication.getInstance().stop();
    }
}
