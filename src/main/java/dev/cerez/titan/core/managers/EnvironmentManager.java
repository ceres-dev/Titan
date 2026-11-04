package dev.cerez.titan.core.managers;

import dev.cerez.titan.command.CommandHander;
import dev.cerez.titan.command.commands.*;
import dev.cerez.titan.command.profilers.FundingOnTimeCommand;
import dev.cerez.titan.command.profilers.GridCommand;
import dev.cerez.titan.command.profilers.TriangularCommand;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.Manager;
import dev.cerez.titan.core.ManagerStrategy;
import dev.cerez.titan.core.PersistentNope;
import dev.cerez.titan.core.event.Listener;
import dev.cerez.titan.core.event.events.EnvironmentManagerListener;
import dev.cerez.titan.core.exception.AlreadySubscribedException;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.infrastructure.model.*;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.storages.StorageManagerJsonLocal;
import dev.cerez.titan.utils.*;
import dev.cerez.titan.utils.telemtry.TelemetryCollector;
import lombok.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
    private final long uptime = System.currentTimeMillis();
    private WebSocketServer webSocketServer = WebSocketServer.empty();
    private final Set<Subscribable> subscriptions = ConcurrentHashMap.newKeySet();
    private final Set<Listener> listenersAnyEvents = ConcurrentHashMap.newKeySet();

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
        managerMap.values().forEach(Manager::stop);
        discordConnector.stop();
        getPort().unsubscribe(null);
    }

    @Override
    public void registerEnvironment(@NotNull EnvironmentManager manager){
        throw new IllegalStateException();
    }

    public void registerManager(@NotNull Manager<?, ?> @NotNull... manager) {
        for (Manager<?, ?> m : manager) {
            if (!this.equals(m.getEnvironmentManager())) m.setEnvironmentManager(this);
            managerMap.put(m.getId(), m);
            Log.info("Manager Registrado: name=%s type=%s".formatted(m.getName(), m.getClass().getSimpleName()));
            // TODO: Esto debe estar dentro de STRATEGY
            if (m instanceof ManagerStrategy<?, ?, ?, ?> strategy){
                strategy.registerListener(new Listener() {
                    @Override
                    public void changeStatus() {
                        webSocketServer.sendMessage(getPort().getStrategy());
                    }
                });
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

        public void executeCommand(String cmd){
            EnvironmentManager.this.commandHandler.dispatchFromRaw(cmd);
        }

        public void addManager(@NotNull Manager<?, ?> @NotNull... manager) {
            for (Manager<?, ?> m : manager) EnvironmentManager.this.managerMap.put(m.getId(), m);
        }

        public @NotNull List<Strategy> getStrategy() {
            return managerMap.values().stream().filter(m -> m instanceof ManagerStrategy<?,?,?,?>).map(m -> (ManagerStrategy<?,?,?,?>) m).map(m -> new Strategy(m.getId(), m.getTypeManager(), m.getName(), m.getClass().getSimpleName(), m.getStatus())).toList();
        }

        public Time getTime(){
            return new Time(System.currentTimeMillis(), connector.getTimeSever());
        }

        public List<Position> getPositions() {
            Map<String, Symbol> symbolMap = connector.fGetAllSymbols();
            Map<String, BinanceConnector.FundingRate> nextFundingMap = connector.fGetFundingRate();
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
            Map<String, BigDecimal> BalanceFuture = connector.fGetBalanceAvailable();
            Map<String, BigDecimal> balanceFutureTotal = connector.fGetBalanceValued();
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

        public void transferBalance(String asset, BigDecimal amount, BinanceConnector.Transfer transfer) {
            connector.wTransfer(null, transfer, asset, amount);
            executor.schedule(() -> webSocketServer.sendMessage(getPort().getBalance()), 2, TimeUnit.SECONDS);
        }

        public void subscribe(@NotNull Subscribe subscribe) {
            if (subscribesLive.contains(subscribe)) {
                throw new AlreadySubscribedException();
            }
            subscribesLive.add(subscribe);
            subscribe.getSubscribable(EnvironmentManager.this).subscribe();
        }

        public void unsubscribe(@Nullable Subscribe subscribe) {
            if (subscribe == null){
                subscribesLive.forEach(sub -> sub.getSubscribable(EnvironmentManager.this).revoke());
                subscribesLive.clear();
            }else {
                if (subscribesLive.contains(subscribe)) {
                    subscribe.getSubscribable(EnvironmentManager.this).revoke();
                    subscribesLive.remove(subscribe);
                }
            }
        }

        private final Set<Subscribe> subscribesLive = new HashSet<>();

        @RequiredArgsConstructor
        public enum Subscribe {
            BALANCE(manager -> List.of(
                    Revocable.from(manager.executor.scheduleAtFixedRate(() -> manager.webSocketServer.sendMessage(manager.getPort().getBalance()), 1, 1, TimeUnit.MINUTES)),
                    manager.connector.wuCreateEventAccountUpdate((update) -> manager.webSocketServer.sendMessage(manager.getPort().getBalance()))
            )),
            POSITIONS(manager -> List.of(
                    Revocable.from(manager.executor.scheduleAtFixedRate(() -> manager.webSocketServer.sendMessage(manager.getPort().getPositions()), 15, 15, TimeUnit.SECONDS)),
                    manager.connector.wuCreateEventAccountUpdate((update) -> manager.webSocketServer.sendMessage(manager.getPort().getBalance()))
            )),
            TELEMETRY(manager -> List.of(Revocable.from(manager.executor.scheduleAtFixedRate(() -> manager.webSocketServer.sendMessage(manager.getPort().getTelemetry()), 5, 5, TimeUnit.SECONDS)))),
//            STRATEGY(manager -> manager.managerMap.values().stream().filter(m -> m instanceof ManagerStrategy<?,?,?,?>).map(m -> (ManagerStrategy<?,?,?,?>) m).map(strategy -> strategy.registerListener(new Listener() {
//                @Override
//                public void changeStatus() {
//                    manager.webSocketServer.sendMessage(manager.getPort().getStrategy());
//                }
//            })).toList()),
            LOGS(manager -> List.of(Log.CONSUMER_APPENDER.addConsumer((log) -> manager.webSocketServer.sendMessage(List.of(new LogRecord(Utils.cleanAnsi(log.getMessage()), log.getThreadName(), log.getLevel().toString(), log.getTimeStamp()))))));

            public final Function<EnvironmentManager, List<Revocable>> onSubscribe;
            public final Map<UUID, Subscribable> subscribes = new HashMap<>();

            @Contract(value = "_ -> new", pure = true)
            public @NotNull Subscribable getSubscribable(EnvironmentManager env) {
                return subscribes.computeIfAbsent(env.getIdOwner(), (uuid) -> new Subscribable() {
                    private final List<Revocable> revocables = new ArrayList<>();

                    @Override
                    public void revoke() {
                        revocables.forEach(Revocable::revoke);
                        revocables.clear();
                    }

                    @Override
                    public void subscribe() {
                        revocables.addAll(onSubscribe.apply(env));
                    }
                });
            }
        }
    }
}
