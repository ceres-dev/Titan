package dev.cerez.titan.command.commands;

import dev.cerez.titan.Titan;
import dev.cerez.titan.command.BaseCommand;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExitCommand extends BaseCommand {

    public ExitCommand() {
        super("exit");
    }

    @Override
    public void execute(@NotNull List<String> args) {
        Titan.getInstance().stop();
    }
}
