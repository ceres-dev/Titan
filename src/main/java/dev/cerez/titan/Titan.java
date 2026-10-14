package dev.cerez.titan;

import dev.cerez.titan.command.CommandHander;
import dev.cerez.titan.command.commands.*;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.environment.EnvironmentManager;
import dev.cerez.titan.infrastructure.TitanApplication;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.StorageManagerJsonLocal;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Titan implements Switch {

    // No hacer no conectores en:
    // crypto.com
    // okx (Muy difícil)

    @Getter
    private static final Titan instance = new Titan();
    private static final CommandHander commandHandler = new CommandHander();
    public static final boolean IS_TESTNET = false;

    @Getter
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(16, Utils.getThreadFactory(this));
    @Getter private final TitanApplication titanApplication = new TitanApplication();
    private final Map<UUID, EnvironmentManager> environmentManagers = new ConcurrentHashMap<>();

    static {
        commandHandler.registerCommand(
                new SpringBootCommand(),
                new ExitCommand(),
                new TriangularCommand(),
                new FundingCommand(),
                new CheckFundingCommand(),
                new GridCommand(),
                new FundingOnTimeCommand()
        );
    }

    public static void main(String[] args) {
//        StorageManager storageManager = new StorageManagerJsonLocal(Utils.getRootId());
//        BinanceConnector binanceConnector = new BinanceConnector(storageManager.getConfigProvider(BaseConnector.ConnectorConfig.class, null));
//        binanceConnector.start();
//        long current = System.currentTimeMillis();
//        List<BinanceConnector.FundingRate> targetFiltered = binanceConnector.fGetFundingRate().values().stream()
//                .filter(f -> (f.nextFundingTime() - current) < TimeUnit.HOURS.toMillis(1))
//                .filter(f -> f.symbol().endsWith("USDT"))
//                .filter(f -> f.nextFundingRateAbs().compareTo(BigDecimal.valueOf(0.0015)) > 0)
//                .toList();
//
//        List<BinanceConnector.FundingRate> targetLimited = targetFiltered.stream()
//                .sorted(Comparator.comparing(BinanceConnector.FundingRate::nextFundingRateAbs).reversed())
//                .limit(6)
//                .toList();
//
//        System.out.println(targetLimited.stream().map(f ->f.symbol() + "@" + f.nextFundingRate()).toList());
//        System.out.println(targetFiltered.stream().map(f ->f.symbol() + "@" + f.nextFundingRate()).toList());
//        System.out.println(targetLimited.getLast());
        try {
            commandHandler.init();
        } catch (Exception e) {
            Log.exception(e);
        }
    }

    public EnvironmentManager getEnvironmentManager(UUID idUser) {
        return this.environmentManagers.computeIfAbsent(idUser, EnvironmentManager::create);
    }

    @Override
    public void start() {
        Log.info("""
                                                          ___           ___    \s
                      ___       ___           ___        /  /\\         /__/\\   \s
                     /  /\\     /  /\\         /  /\\      /  /::\\        \\  \\:\\  \s
                    /  /:/    /  /:/        /  /:/     /  /:/\\:\\        \\  \\:\\ \s
                   /  /:/    /__/::\\       /  /:/     /  /:/~/::\\   _____\\__\\:\\\s
                  /  /::\\    \\__\\/\\:\\__   /  /::\\    /__/:/ /:/\\:\\ /__/::::::::\\
                 /__/:/\\:\\      \\  \\:\\/\\ /__/:/\\:\\   \\  \\:\\/:/__\\/ \\  \\:\\~~\\~~\\/
                 \\__\\/  \\:\\      \\__\\::/ \\__\\/  \\:\\   \\  \\::/       \\  \\:\\  ~~~\s
                      \\  \\:\\     /__/:/       \\  \\:\\   \\  \\:\\        \\  \\:\\    \s
                       \\__\\/     \\__\\/         \\__\\/    \\  \\:\\        \\  \\:\\   \s
                                                         \\__\\/         \\__\\/   \s
                """);
        titanApplication.start();
    }

    @Override
    public void stop() {
        System.exit(0);
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}