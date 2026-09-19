package dev.cerez.titan;

import dev.cerez.titan.command.CommandHander;
import dev.cerez.titan.command.commands.*;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.discord.DiscordConnector;
import lombok.Getter;

import java.util.Comparator;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

public class Main {

    // No hacer no conectores en:
    // crypto.com
    // okx (Muy difícil)

    @Getter
    private static final Main instance = new Main();
    private static final CommandHander commandHandler = new CommandHander();

    public static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(16);
    public static final boolean IS_TESTNET = false;

    @Getter
    public DiscordConnector discordConnector = new DiscordConnector();

    public static void main(String[] args) {
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
            Main.getInstance().getDiscordConnector().sendMessage("Error Critico: " + e.getMessage());
            e.printStackTrace();
        }
//        BinanceConnector connector = new BinanceConnector();
//        connector.start();
//        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
//        connector.fGetFundingRate().values().stream().min(Comparator.comparingDouble(f -> f.nextFundingRate().doubleValue())).ifPresent(System.out::println);


        // TODO: code -1021 reenviar la solicitud
    }


    public static void exit(){
        System.exit(0);
    }
}