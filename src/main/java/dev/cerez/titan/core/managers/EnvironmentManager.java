package dev.cerez.titan.core.managers;

import dev.cerez.titan.Titan;
import dev.cerez.titan.command.CommandHander;
import dev.cerez.titan.command.commands.*;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.Manager;
import dev.cerez.titan.core.ManagerStrategy;
import dev.cerez.titan.core.PersistentNope;
import dev.cerez.titan.core.event.Listener;
import dev.cerez.titan.core.event.events.EnvironmentManagerListener;
import dev.cerez.titan.core.exception.ConfigMalformatException;
import dev.cerez.titan.core.exception.ManagerIsNotFoundException;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.infrastructure.model.Balance;
import dev.cerez.titan.infrastructure.model.LogRecord;
import dev.cerez.titan.infrastructure.model.Strategy;
import dev.cerez.titan.infrastructure.model.Time;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.storages.StorageManagerJsonLocal;
import dev.cerez.titan.utils.Messageable;
import dev.cerez.titan.utils.NoAutoRegister;
import dev.cerez.titan.utils.Ownership;
import dev.cerez.titan.utils.Utils;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;

@Data
@EqualsAndHashCode(callSuper = true)
public final class EnvironmentManager extends ManagerStrategy<ConfigNope, PersistentNope, BinanceConnector, EnvironmentManagerListener> implements Ownership, NoAutoRegister {

    @NotNull
    private final UUID idOwner;
    @Getter(AccessLevel.NONE)
    private final Map<UUID, Manager<?, ?>> managerMap = new HashMap<>();
    private final EnvironmentPort port = new EnvironmentPort();
    private final StorageManager storageManager;
    private final DiscordConnector discordConnector;
    private final CommandHander commandHandler;
    private Messageable messageable = message -> {};

    private EnvironmentManager(@NotNull StorageManager storageManager, @NotNull BinanceConnector binanceConnector, @NotNull UUID idOwner) {
        super(storageManager.createProfilerManager(ConfigNope.class, PersistentNope.class), binanceConnector, TypeManager.ENVIRONMENT);
        this.storageManager = storageManager;
        this.idOwner = idOwner;
        this.commandHandler = new CommandHander(this);
        commandHandler.registerCommand(
                new ExitCommand(this),
                new TriangularCommand(this),
                new FundingCommand(this),
                new CheckFundingCommand(this),
                new GridCommand(this),
                new FundingOnTimeCommand(this)
        );
        discordConnector = new DiscordConnector(storageManager.saveIfNotExits(DiscordConnector.DiscordConfig.builder().build()));
        connector.wuCreateEventAccountUpdate((update) -> {
            messageable.sendMessage(getPort().getBalance());
            messageable.sendMessage(getPort().getPositions());
        }, this);
        Log.CONSUMER_APPENDER.addConsumer((log) ->
        {
            messageable.sendMessage(List.of(new LogRecord(Utils.cleanAnsi(log.getMessage()), log.getThreadName(), log.getLevel().toString(), log.getTimeStamp())));
        }        );
    }

    @Contract(pure = true)
    public static @NotNull EnvironmentManager create(@NotNull UUID idUser) {
        StorageManager storageManager = new StorageManagerJsonLocal(idUser);
        BinanceConnector binanceConnector = new BinanceConnector(
                storageManager.loadOrSaveDefault(
                        BaseConnector.ConnectorConfig.builder()
                                .isTestNet(Titan.IS_TESTNET)
                                .maxStreamsPerSubscribe(100)
                                .build()
                )
        );
        var environment = new EnvironmentManager(
                storageManager,
                binanceConnector,
                idUser
        );
        environment.start();
        return environment;
    }

    @Override
    protected void internalStart() {
        connector.start();
        discordConnector.start();
        commandHandler.start();
    }

    @Override
    protected void internalStop() {
        connector.stop();
        commandHandler.stop();
        discordConnector.stop();
    }

    @Override
    public void registerEnvironment(@NotNull EnvironmentManager manager){
        throw new IllegalStateException();
    }

    public void registerManager(@NotNull Manager<?, ?> @NotNull... manager) {
        for (Manager<?, ?> m : manager) {
            if (!this.equals(m.getEnvironmentManager())) m.setEnvironmentManager(this);
            managerMap.put(m.getId(), m);
            if (m instanceof ManagerStrategy<?, ?, ?, ?> strategy){
                executor.execute(() -> strategy.registerListener(new Listener() {
                    @Override
                    public void changeStatus() {
                        messageable.sendMessage(getPort().getStrategy());
                    }
                }));
            }
        }
    }

    public void unregisterManager(@NotNull UUID @NotNull... id) {
        for (UUID i : id) managerMap.remove(i);
    }

    @Contract(pure = true)
    public Manager<?, ?> getManager(@NotNull UUID id) {
        return managerMap.get(id);
    }

    @Override
    public @NotNull TypeManager getTypeManager() {
        return TypeManager.FUNDING_NEUTRAL;
    }

    public class EnvironmentPort {

        public void removeManager(@NotNull UUID @NotNull... id) throws ManagerIsNotFoundException {
            for (UUID i : id) {
                try {
                    EnvironmentManager.this.managerMap.remove(i).stop();
                } catch (NullPointerException e) {
                    throw new ManagerIsNotFoundException();
                }
            }
        }

        public void executeCommand(String cmd){
            EnvironmentManager.this.commandHandler.dispatch(cmd);
        }

        public void addManager(@NotNull Manager<?, ?> @NotNull... manager) {
            for (Manager<?, ?> m : manager) EnvironmentManager.this.managerMap.put(m.getId(), m);
        }

        @Deprecated
        public UUID createManager(@NotNull ManagerBuilder managerBuilder) throws ManagerIsNotFoundException  {
            UUID uuid = UUID.randomUUID();
            try {
                Manager<?, ?> manager = managerBuilder.manager.getConstructor(Object.class, BinanceConnector.class).newInstance(managerBuilder.config, connector);
                manager.setName(managerBuilder.name);
                EnvironmentManager.this.managerMap.put(uuid, manager);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new ConfigMalformatException();
            }
            return uuid;
        }

        public @NotNull List<Strategy> getStrategy() {
            Log.info(managerMap.values().toString());
            return managerMap.values().stream().filter(m -> m instanceof ManagerStrategy<?,?,?,?>).map(m -> (ManagerStrategy<?,?,?,?>) m).map(m -> new Strategy(m.getId(), m.getTypeManager(), m.getName(), m.getClass().getSimpleName(), m.getStatus())).toList();
        }

        public Time getTime(){
            return new Time(System.currentTimeMillis(), connector.getTimeSever());
        }

        public long getPing(){
            return connector.fPing();
        }

        public List<BinanceConnector.FuturePosition> getPositions() {
            return connector.fGetPositions();
        }

        public Balance getBalance() {
            Map<String, BigDecimal> BalanceSpot = connector.sGetBalance();
            Map<String, BigDecimal> BalanceFuture = connector.fGetBalance();
            return new Balance(
                    Utils.removeIf(BalanceSpot, (k, v) -> v.signum() == 0),
                    Utils.removeIf(BalanceFuture, (k, v) -> v.signum() == 0)
            );
        }

        public List<LogRecord> getLogs(){
            return Log.HISTORY_APPENDER.getHistory().stream().map(i -> new LogRecord(Utils.cleanAnsi(i.getMessage()), i.getThreadName(), i.getLevel().toString(), i.getTimeStamp())).toList();
        }

        public record ManagerBuilder(Class<? extends Manager<?, ?>> manager, Object config, String name) {}
    }
}
