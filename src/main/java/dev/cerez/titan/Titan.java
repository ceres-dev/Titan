package dev.cerez.titan;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.infrastructure.TitanApplication;
import dev.cerez.titan.log.HistoryAppender;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.Getter;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class Titan implements Switch {

    // No hacer no conectores en:
    // crypto.com
    // okx (Muy difícil)

    @Getter
    private static final Titan instance = new Titan();
    public static final boolean IS_TESTNET = false;

    @Getter
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(16, Utils.getThreadFactory(this));
    @Getter private final TitanApplication titanApplication = new TitanApplication();
    private final Map<UUID, EnvironmentManager> environmentManagers = new ConcurrentHashMap<>();

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
        Titan.getInstance().start();
        Log.configure();
        try {

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