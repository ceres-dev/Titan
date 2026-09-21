package dev.cerez.titan.command.commands;

import dev.cerez.titan.Titan;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
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
        GridManager.GridManagerConfig config = GridManager.GridManagerConfig.builder()
                .baseAsset("SPY")
                .quoteAsset("USDT")
                .stepSize(new BigDecimal("1"))
                .sizePerOrderBaseAsset(new BigDecimal("0.01"))
                .leverage(5)
                .logsEndPoints(false)
                .sideGrid(SideGrid.LONG)
                .amountPriceOffset(15)
                .build();
        GridManager manager = new GridManager(config, new BinanceConnector());
        discordConnector.setStatusProfiler(manager);
        discordConnector.start();
        manager.start();
    }
}
