package dev.cerez.titan.command.commands;

import dev.cerez.titan.Titan;
import dev.cerez.titan.command.BaseCommand;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SpringBootCommand extends BaseCommand {
    public SpringBootCommand() {
        super("SpringBoot", "s");
    }

    @SneakyThrows
    @Override
    public void execute(@NotNull List<String> args) {
        Titan.getInstance().start();
    }
}
