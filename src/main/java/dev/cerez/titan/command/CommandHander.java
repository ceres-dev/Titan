package dev.cerez.titan.command;

import dev.cerez.titan.command.exception.CommandNameUndefineException;
import dev.cerez.titan.command.exception.CommandNotFoundException;
import dev.cerez.titan.command.exception.CommandSetupDispatchPostSetupException;
import dev.cerez.titan.command.exception.CommandUserException;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class CommandHander implements Switch {

    private final HashMap<String, BaseCommand> commands = new LinkedHashMap<>();
    private final HashMap<String, String> aliases = new HashMap<>();
    private final InputUser input = new InputUser();
    private final ScheduledExecutorService executor;
    private boolean isPostSetup = false;

    public CommandHander() {
        this.executor = Executors.newScheduledThreadPool(4, Utils.getThreadFactory(this));
    }

    public void dispatch(BaseCommand command, String @NotNull ... args) {
        if (args.length == 0) {
            Log.warning("No hay commandos");
            return;
        }
        try {
            if (args.length > 1) {
                command.execute(Arguments.buildArgs(command, Arrays.stream(Arrays.copyOfRange(args, 1, args.length)).toList()));
            }else {
                command.execute(Arguments.buildArgsEmpty());
            }
        }catch (CommandUserException e){
            Log.error("Ops!! command=%s exception=%s reason=%s", command.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getReason());
        }catch (Exception e){
            Log.exception(e);
        }
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
        for (String cmd : args) {
            List<String> arguments = parsingArguments(cmd);
            BaseCommand baseCommand = commands.getOrDefault(arguments.getFirst(), commands.get(aliases.get(args[0])));
            if (baseCommand == null) {
                throw new CommandNotFoundException();
            }
            if (baseCommand instanceof ProfilerSetup){
                if (isPostSetup) {
                    throw new CommandSetupDispatchPostSetupException();
                }
            }
            dispatch(baseCommand, cmd.split(" "));
        }
    }

    private static @NotNull List<String> parsingArguments(@NotNull String cmd) {
        List<String> argumentRaw = new LinkedList<>();
        for (String arg : List.of(cmd.split(" "))) {
            if (arg.startsWith("\"")) argumentRaw.add("\"");
            String[] split = arg.split("\"");
            for (int i = 0; i < split.length; i++) {
                argumentRaw.add(split[0]);
                if (i > 0 && i < split.length-1) argumentRaw.add("\"");
            }
            if (arg.endsWith("\"")) argumentRaw.add("\"");
        }

        argumentRaw.removeIf(String::isBlank);

        List<String> arguments = new LinkedList<>();
        AtomicBoolean isInQuotationMarks = new AtomicBoolean(false);
        LinkedList<String> argumentInQuotaMarks = new LinkedList<>();
        for (String arg : argumentRaw) {
            if (arg.contains("\"")) {
                isInQuotationMarks.set(!isInQuotationMarks.get());
            }
            argumentInQuotaMarks.add(arg);
            if (!isInQuotationMarks.get()) {
                arguments.add(String.join(" ", argumentInQuotaMarks));
                argumentInQuotaMarks.clear();
            }
        }

        if (arguments.isEmpty()) {
            throw new CommandNameUndefineException();
        }
        return arguments;
    }

    public void registerCommand(BaseCommand @NotNull ... command) {
        for (BaseCommand c : command) {
            if (commands.containsKey(c.getName())) {
                throw new IllegalArgumentException("Command with name %s already exists".formatted(c.getName()));
            }
            if (aliases.containsKey(c.getAlias())) {
                throw new IllegalArgumentException("Command with aliases %s already exists".formatted(c.getName()));
            }
            commands.put(c.getName(), c);
            aliases.put(c.getAlias(), c.getName());
        }
    }
}
