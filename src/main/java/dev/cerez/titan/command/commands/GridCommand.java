package dev.cerez.titan.command.commands;

import dev.cerez.titan.Titan;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
import dev.cerez.titan.io.StorageManager;
import dev.cerez.titan.io.StorageManagerJsonLocal;
import dev.cerez.titan.utils.Utils;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;

@ToString
public class GridCommand extends BaseCommand {
    public GridCommand() {
        super("grid", "g");
    }

    @Override
    public void execute(@NotNull List<String> args) {
        DiscordConnector discordConnector = Titan.getInstance().getDiscordConnector();
        GridManager.GridManagerConfiguration config = GridManager.GridManagerConfiguration.builder()
                .baseAsset("SPY")
                .quoteAsset("USDT")
                .stepSizeHighActivity(new BigDecimal("0.7"))
                .stepSizeMediumActivity(new BigDecimal("0.5"))
                .stepSizeLowActivity(new BigDecimal("0.4"))
                .sizePerOrderBaseAsset(new BigDecimal("0.01"))
                .leverage(5)
                .logsEndPoints(false)
                .sideGrid(SideGrid.LONG)
                .amountPriceOffset(15)
                .build();
        StorageManager storageManager = new StorageManagerJsonLocal(Utils.getRootId());
        BinanceConnector binanceConnector = new BinanceConnector();
        binanceConnector.start();
        GridManager manager = new GridManager(config, binanceConnector, storageManager);
        discordConnector.setStatusProfiler(manager);
        discordConnector.start();
        manager.start();
    }
}
