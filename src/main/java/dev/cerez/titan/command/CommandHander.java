package dev.cerez.titan.command;

import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CommandHander implements Switch {

    private final HashMap<String, BaseCommand> commands = new LinkedHashMap<>();
    private final HashMap<String, String> aliases = new HashMap<>();
    private final HashMap<Integer, String> indexCommands = new HashMap<>();
    private final InputUser input = new InputUser();
    private final Executor executor = Executors.newScheduledThreadPool(4, Utils.getThreadFactory(this));

    public void dispatch(@NotNull String cmd){
        dispatch(cmd.split(" "));
    }

    public void dispatch(String @NotNull ... args) {
        if (args.length == 0) {
            Log.warning("No hay commandos");
            return;
        }

        BaseCommand command = commands.getOrDefault(args[0], commands.get(aliases.get(args[0])));
        if (command == null) {
            Log.warning("No such command: " + args[0]);
            return;
        }
        if (args.length > 1) {
            command.execute(Arrays.stream(Arrays.copyOfRange(args, 1, args.length)).toList());
        }else {
            command.execute(List.of());
        }
    }

    @Getter
    public boolean running = false;

    @Override
    public void start() {
        if (running) return;
        running = true;
        executor.execute(() -> {
            int i = 0;
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                int index = i++;
                System.out.printf("\t[%d/%s]: %s%n", index, entry.getKey(), entry.getValue());
                indexCommands.put(index, entry.getValue());
            }
            String raw = input.in("Select: ");
            String commandName = aliases.get(raw);
            if (commandName == null) {
                dispatch(indexCommands.get(Integer.parseInt(raw)));
            }else {
                dispatch(commandName);
            }
            while (running) dispatch(input.in(""));
        });
    }

    @Override
    public void stop() {
        running = false;
    }

    public void registerCommand(BaseCommand @NotNull ... command) {
        for (BaseCommand c : command) {
            commands.put(c.getName(), c);
            aliases.put(c.getAlias(), c.getName());
        }
    }
}
