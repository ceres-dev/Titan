package dev.cerez.titan;

import com.sun.tools.javac.Main;
import dev.cerez.titan.command.CommandHander;
import dev.cerez.titan.command.commands.*;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.infrastructure.TitanApplication;
import dev.cerez.titan.utils.Switch;
import dev.cerez.titan.utils.Utils;
import lombok.Getter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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

    @Getter public final DiscordConnector discordConnector = new DiscordConnector();
    @Getter public final TitanApplication titanApplication = new TitanApplication();

    public static void main(String[] args) {
        instance.discordConnector.start();

//        instance.start();
        commandHandler.registerCommand(
                new ExitCommand(),
                new TriangularCommand(),
                new FundingCommand(),
                new CheckFundingCommand(),
                new GridCommand(),
                new DataRecoveryCommand()
        );
        try {
            commandHandler.init();
        } catch (Exception e) {
            Titan.getInstance().getDiscordConnector().sendMessage("Error Critico: " + e.getMessage());
            e.printStackTrace();
        }
//        BinanceConnector connector = new BinanceConnector();
//        connector.start();
//        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
//        connector.fGetFundingRate().values().stream().min(Comparator.comparingDouble(f -> f.nextFundingRate().doubleValue())).ifPresent(System.out::println);


        // TODO: code -1021 reenviar la solicitud
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