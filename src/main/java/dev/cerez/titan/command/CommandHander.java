package dev.cerez.titan.command;

import dev.cerez.titan.command.exception.CommandNameUndefineException;
import dev.cerez.titan.command.exception.CommandNotFoundException;
import dev.cerez.titan.command.exception.CommandSetupDispatchPostSetupException;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

public class CommandHander implements Switch {

    private final HashMap<String, BaseCommand> commands = new LinkedHashMap<>();
    private final HashMap<String, String> aliases = new HashMap<>();
    private final InputUser input = new InputUser();
    private final ScheduledExecutorService executor;
    private boolean isPostSetup = false;

    public CommandHander(@NotNull EnvironmentManager environmentManager) {
        this.executor = Executors.newScheduledThreadPool(4, Utils.getThreadFactory(this, environmentManager));
    }

    public void dispatch(BaseCommand command, String @NotNull ... args) {
        if (args.length == 0) {
            Log.warning("No hay commandos");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                if (args.length > 1) {
                    command.execute(Arrays.stream(Arrays.copyOfRange(args, 1, args.length)).toList());
                }else {
                    command.execute(List.of());
                }
            }catch (Exception e){
                Log.exception(e);
            }
        }, executor);
    }

    @Getter
    public boolean running = false;

    @Override
    public void start() {
        if (running) return;
        running = true;
        executor.execute(() -> {
            HashMap<Integer, String> indexCommands = new HashMap<>();
            StringBuilder sb = new StringBuilder();
            int i = 0;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                int index = i++;
                sb.append("\t[%d/%s]: %s%n".formatted(index, entry.getKey(), entry.getValue()));
                indexCommands.put(index, entry.getValue());
            }
            System.out.println(sb);
            String raw = input.in("Select: ");
            String commandName = aliases.get(raw);
            try {
                if (commandName == null) {
                    dispatchFromRaw(indexCommands.get(Integer.parseInt(raw)));
                }else {
                    dispatchFromRaw(commandName);
                }
            } catch (Exception e) {
                Log.exception(e);
            }
            isPostSetup = true;
            while (running) {
                try {
                    dispatchFromRaw(input.in(""));
                } catch (Exception e) {
                    Log.exception(e);
                }
            }
        });
    }

    @Override
    public void stop() {
        running = false;
    }

    public void dispatchFromRaw(@NotNull String raw) {
        String[] args = raw.split(";");
        for (String arg : args) {
            String[] split = arg.split(" ");
            if (split.length == 0) {
                throw new CommandNameUndefineException();
            }
            BaseCommand baseCommand = commands.getOrDefault(split[0], commands.get(aliases.get(args[0])));
            if (baseCommand == null) {
                throw new CommandNotFoundException();
            }
            if (baseCommand instanceof ProfilerSetup){
                if (isPostSetup) {
                    throw new CommandSetupDispatchPostSetupException();
                }
            }
            dispatch(baseCommand, arg.split(" "));
        }
    }

    public void registerCommand(BaseCommand @NotNull ... command) {
        for (BaseCommand c : command) {
            commands.put(c.getName(), c);
            aliases.put(c.getAlias(), c.getName());
        }
    }
}
