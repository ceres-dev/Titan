package dev.cerez.titan.core.managers;

import dev.cerez.titan.command.CommandHander;
import dev.cerez.titan.command.commands.*;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.model.Symbol;
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
import dev.cerez.titan.infrastructure.model.*;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.storages.StorageManagerJsonLocal;
import dev.cerez.titan.utils.WebSocketServer;
import dev.cerez.titan.utils.NoAutoRegister;
import dev.cerez.titan.utils.Ownership;
import dev.cerez.titan.utils.Utils;
import dev.cerez.titan.utils.telemtry.TelemetryCollector;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private final TelemetryCollector telemetry;
    private WebSocketServer webSocketServer = message -> {};
    private final long uptime = System.currentTimeMillis();

    private EnvironmentManager(@NotNull StorageManager storageManager, @NotNull BinanceConnector binanceConnector, @NotNull UUID idOwner) {
        super(storageManager.createProfilerManager(ConfigNope.class, PersistentNope.class), binanceConnector, TypeManager.ENVIRONMENT);
        this.storageManager = storageManager;
        this.idOwner = idOwner;
        this.commandHandler = new CommandHander(this);
        TelemetryCollector.TelemetryConfig config = TelemetryCollector.TelemetryConfig.builder()
                .maxDelaysDeltaComputeNanoTime(100)
                .stepsAddDelayComputeNanoTime(5)
                .build();
        this.telemetry = new TelemetryCollector(config);
        connector.setTelemetry(telemetry);
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
            webSocketServer.sendMessage(getPort().getBalance());
            webSocketServer.sendMessage(getPort().getPositions());
        }, this);
        Log.CONSUMER_APPENDER.addConsumer((log) ->
                webSocketServer.sendMessage(List.of(new LogRecord(Utils.cleanAnsi(log.getMessage()), log.getThreadName(), log.getLevel().toString(), log.getTimeStamp()))));
        executor.scheduleAtFixedRate(() -> webSocketServer.sendMessage(getPort().getTelemetry()), 5, 5, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(() -> webSocketServer.sendMessage(getPort().getPositions()), 15, 15, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(() -> webSocketServer.sendMessage(getPort().getBalance()), 5, 5, TimeUnit.MINUTES);

    }

    @Contract(pure = true)
    public static @NotNull EnvironmentManager create(@NotNull UUID idUser) {
        StorageManager storageManager = new StorageManagerJsonLocal(idUser);
        BinanceConnector binanceConnector = new BinanceConnector(
                storageManager.loadOrSaveDefault(
                        BaseConnector.ConnectorConfig.builder()
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
                        webSocketServer.sendMessage(getPort().getStrategy());
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
            return managerMap.values().stream().filter(m -> m instanceof ManagerStrategy<?,?,?,?>).map(m -> (ManagerStrategy<?,?,?,?>) m).map(m -> new Strategy(m.getId(), m.getTypeManager(), m.getName(), m.getClass().getSimpleName(), m.getStatus())).toList();
        }

        public Time getTime(){
            return new Time(System.currentTimeMillis(), connector.getTimeSever());
        }

        public long getPing(){
            return connector.fPing();
        }

        public List<Position> getPositions() {
            Map<String, Symbol> symbolMap = connector.fGetAllSymbols();
            Map<String, BinanceConnector.FundingRate> nextFundingMap = connector.fGetFundingRate();;
            return connector.fGetPositions().stream().map((futurePosition -> {
                Symbol symbol = symbolMap.get(futurePosition.symbol());
                BigDecimal nextFunding = nextFundingMap.get(futurePosition.symbol()).nextFundingRate();
                return new Position(
                        symbol.getBaseAsset(),
                        symbol.getQuoteAsset(),
                        futurePosition.quantity(),
                        futurePosition.entryPriceAvg(),
                        futurePosition.pnlUnrealize(),
                        nextFunding
                );
            })).toList();
        }

        private static final Set<BinanceConnector.IncomeType> TYPES_COMPUTE = Set.of(
                BinanceConnector.IncomeType.REALIZED_PNL,
                BinanceConnector.IncomeType.FUNDING_FEE,
                BinanceConnector.IncomeType.SPECIAL_FUNDING_FEE,
                BinanceConnector.IncomeType.COMMISSION,
                BinanceConnector.IncomeType.OPTIONS_PREMIUM_FEE,
                BinanceConnector.IncomeType.INSURANCE_CLEAR,
                BinanceConnector.IncomeType.POSITION_LIMIT_INCREASE_FEE,
                BinanceConnector.IncomeType.DELIVERED_SETTELMENT,
                BinanceConnector.IncomeType.OPTIONS_SETTLE_PROFIT
        );

        public Balance getBalance() {
            Map<String, BigDecimal> BalanceSpot = connector.sGetBalance();
            Map<String, BigDecimal> BalanceFuture = connector.fGetBalance();
            Map<String, BigDecimal> balanceFutureTotal = connector.fGetBalanceTotal();
            long currentTime = System.currentTimeMillis();
            double total = balanceFutureTotal.values().stream().mapToDouble(BigDecimal::doubleValue).sum();
            List<BinanceConnector.Transaction> transactions = connector.fGetTransaction(currentTime - TimeUnit.DAYS.toMillis(7), currentTime);
            double roiAbsolute7D = transactions.stream().filter(transaction -> TYPES_COMPUTE.contains(transaction.incomeType())).mapToDouble(transaction -> transaction.amount().doubleValue()).sum();
            long ago1D = currentTime - TimeUnit.DAYS.toMillis(1);
            double roiAbsolute1D = transactions.stream().filter(transaction -> transaction.date() > ago1D).filter(transaction -> TYPES_COMPUTE.contains(transaction.incomeType())).mapToDouble(transaction -> transaction.amount().doubleValue()).sum();

            double roiPercent7D = roiAbsolute7D / total;
            double roiPercent1D = roiAbsolute1D / total;

            return new Balance(
                    Utils.removeIf(BalanceSpot, (k, v) -> v.signum() == 0),
                    Utils.removeIf(BalanceFuture, (k, v) -> v.signum() == 0),
                    Utils.removeIf(balanceFutureTotal, (k, v) -> v.signum() == 0),
                    new Balance.Roi(
                            roiPercent7D,
                            roiPercent1D,
                            roiAbsolute7D,
                            roiAbsolute1D
                    )
            );
        }

        public List<LogRecord> getLogs(){
            return Log.HISTORY_APPENDER.getHistory().stream().map(i -> new LogRecord(Utils.cleanAnsi(i.getMessage()), i.getThreadName(), i.getLevel().toString(), i.getTimeStamp())).toList();
        }

        public Telemetry getTelemetry() {
            TelemetryCollector.TelemetrySnapshot snapshot = telemetry.getSnapshot();
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            BaseConnector.RateLimit rateLimit = connector.getRateLimitFuture();
            return new Telemetry(
                    new Telemetry.Connector(
                            snapshot.getRequestLastHour(),
                            rateLimit.getUse(),
                            rateLimit.getMax(),
                            connector.wsPing()
                    ),
                    new Telemetry.System(
                            totalMemory,
                            usedMemory,
                            System.currentTimeMillis() - uptime
                    )
            );
        }

        public record ManagerBuilder(Class<? extends Manager<?, ?>> manager, Object config, String name) {}
    }
}
