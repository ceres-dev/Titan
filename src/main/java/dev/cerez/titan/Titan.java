package dev.cerez.titan;

import dev.cerez.titan.command.CommandHander;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.environment.EnvironmentManager;
import dev.cerez.titan.core.strategy.fundingO.FundingOnTimeManager;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.infrastructure.TitanApplication;
import dev.cerez.titan.io.ConfigurationProvider;
import dev.cerez.titan.io.StorageManagerJsonLocal;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class Titan implements Switch {

    // No hacer no conectores en:
    // crypto.com
    // okx (Muy difícil)

    @Getter
    private static final Titan instance = new Titan();
    private static final CommandHander commandHandler = new CommandHander();
    @Getter
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(16, Utils.getThreadFactory());
    public static final boolean IS_TESTNET = false;

    @Getter private final DiscordConnector discordConnector = new DiscordConnector();
    @Getter private final TitanApplication titanApplication = new TitanApplication();
    @Getter private final BinanceConnector publicConnector = new BinanceConnector();
    private final Map<UUID, EnvironmentManager> environmentManagers = new HashMap<>();

    public static void main(String[] args) {
        instance.discordConnector.start();
        BinanceConnector connector = new BinanceConnector();
        connector.start();
        if (args.length != 0 && args[0].equals("SpringBoot")) {
            instance.start();
        }else {
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
            FundingOnTimeManager fundingManger = new FundingOnTimeManager(FundingOnTimeManager.FundingMangerConfiguration.builder().build(),connector, new StorageManagerJsonLocal(Utils.getRootId()));
            fundingManger.start();
        }

//        commandHandler.registerCommand(
//                new ExitCommand(),
//                new TriangularCommand(),
//                new FundingCommand(),
//                new CheckFundingCommand(),
//                new GridCommand(),
//                new DataRecoveryCommand()
//        );
//        try {
//            commandHandler.init();
//        } catch (Exception e) {
//            Titan.getInstance().getDiscordConnector().sendMessage("Error Critico: " + e.getMessage());
//            e.printStackTrace();
//        }

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