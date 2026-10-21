package dev.cerez.titan.core.managers;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.cerez.titan.Titan;
import dev.cerez.titan.command.CommandHander;
import dev.cerez.titan.command.commands.*;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.ManagerStrategy;
import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.PersistentNope;
import dev.cerez.titan.core.exception.ConfigMalformatException;
import dev.cerez.titan.core.exception.ManagerIsNotFoundException;
import dev.cerez.titan.core.event.events.EnvironmentManagerListener;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.storages.StorageManagerJsonLocal;
import dev.cerez.titan.core.Manager;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;

@Data
@EqualsAndHashCode(callSuper = true)
public final class EnvironmentManager extends ManagerStrategy<ConfigNope, PersistentNope, BinanceConnector, EnvironmentManagerListener> {

    private UUID idOwner;
    private final Map<UUID, Manager<?, ?>> managerMap = new HashMap<>();
    private final CommandHander commandHandler = new CommandHander();
    private final EnvironmentPort port = new EnvironmentPort();
    private final StorageManager storageManager;
    private final DiscordConnector discordConnector;

    private EnvironmentManager(@NotNull StorageManager storageManager, @NotNull BinanceConnector binanceConnector, @NotNull UUID idOwner) {
        super(storageManager.createProfilerManager(ConfigNope.class, PersistentNope.class), binanceConnector, TypeManager.ENVIRONMENT);
        this.storageManager = storageManager;
        this.idOwner = idOwner;
        commandHandler.registerCommand(
                new ExitCommand(this),
                new TriangularCommand(this),
                new FundingCommand(this),
                new CheckFundingCommand(this),
                new GridCommand(this),
                new FundingOnTimeCommand(this)
        );
        discordConnector = new DiscordConnector(storageManager.saveIfNotExits(DiscordConnector.DiscordConfig.builder().build()));
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

        public @NotNull Set<Manager<?, ?>> getStrategy() {
            return new HashSet<>(managerMap.values().stream().filter(m -> m instanceof ManagerStrategy<?,?,?,?>).toList());
        }

        public long getLocalTimestamp() {
            return System.currentTimeMillis();
        }

        public long getRemoteTimestamp() {
            return connector.getTimeSever();
        }

        public long getPing(){
            return connector.fPing();
        }

        public Map<String, BigDecimal> getFutureBalance() {
            return connector.fGetBalance();
        }

        public Map<String, BigDecimal> getSpotBalance() {
            return connector.sGetBalance();
        }

        public List<BinanceConnector.FuturePosition> getPositions() {
            return connector.fGetPositions();
        }

        public List<ILoggingEvent> getLogs(){
            return Log.HISTORY_APPENDER.getHistory();
        }

        public record ManagerBuilder(Class<? extends Manager<?, ?>> manager, Object config, String name) {}
    }
}
