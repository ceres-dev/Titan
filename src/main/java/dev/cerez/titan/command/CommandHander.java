package dev.cerez.titan.command;

import dev.cerez.titan.Log;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandHander {

    private final HashMap<String, BaseCommand> commands = new HashMap<>();
    private final HashMap<String, String> aliases = new HashMap<>();
    private final HashMap<Integer, String> indexCommands = new HashMap<>();
    private final InputUser input = new InputUser();

    @Contract(pure = true)
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

    public boolean running = false;

    @Blocking
    public void init() {
        if (running) return;
        running = true;
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

        // Limpiar consola
//        System.out.print("\033[H\033[2J");
//        System.out.flush();
        while (running) dispatch(input.in(""));
    }

    public void stop(){
        running = false;
    }

    public void registerCommand(BaseCommand @NotNull ... command) {
        for (BaseCommand c : command) {
            commands.put(c.getName(), c);
            aliases.put(c.getAlias(), c.getName());
        }
    }
}
