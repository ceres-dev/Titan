package dev.cerez.titan.command.commands;

import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.event.Listener;
import dev.cerez.titan.core.managers.BalanceMaxRiskManager;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.storage.StorageManager;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@ToString
public class GridCommand extends BaseCommand {
    public GridCommand(EnvironmentManager environmentManager) {
        super("grid", "g", environmentManager);
    }

    @Override
    public void execute(@NotNull List<String> args) {
        StorageManager storageManager = environmentManager.getStorageManager();
        BinanceConnector binanceConnector = environmentManager.getConnector();
        DiscordConnector discordConnector = environmentManager.getDiscordConnector();

        GridManager.GridManagerConfig gridConfigDefault = GridManager.GridManagerConfig.builder()
                .baseAsset("SPY")
                .quoteAsset("USDT")
                .stepSizeHighActivity(new BigDecimal("0.7"))
                .stepSizeMediumActivity(new BigDecimal("0.5"))
                .stepSizeLowActivity(new BigDecimal("0.4"))
                .sizePerOrderBaseAsset(new BigDecimal("0.01"))
                .minOrderGreed(10)
                .minOrderSafe(10)
                .leverage(5)
                .logsEndPoints(false)
                .sideGrid(SideGrid.LONG)
                .amountPriceOffset(15)
                .build();

        FundingOnTimeManager.FundingManagerConfig fundingConfigDefault = FundingOnTimeManager.FundingManagerConfig.builder()
                .sendTrade(true)
                .build();

        BalanceMaxRiskManager.BalanceMaxRiskManagerConfig balanceMaxRiskManagerConfigDefault = BalanceMaxRiskManager.BalanceMaxRiskManagerConfig.builder()
                .balanceDivision(Map.of("SPY", BigDecimal.ONE))
                .build();

        BalanceMaxRiskManager balanceMaxRiskManager = new BalanceMaxRiskManager(storageManager.saveConfig(balanceMaxRiskManagerConfigDefault), binanceConnector);
        GridManager gridManager = new GridManager(storageManager.saveConfig(gridConfigDefault), binanceConnector);
        gridManager.setBalanceMaxRiskManager(balanceMaxRiskManager);
        gridManager.setName("SPY");
        gridManager.start();

        FundingOnTimeManager fundingOnTimeManager = new FundingOnTimeManager(storageManager.saveConfig(fundingConfigDefault), binanceConnector);
        fundingOnTimeManager.setName("Funding");
        fundingOnTimeManager.start();

        discordConnector.setStatusProfiler(gridManager);

        fundingOnTimeManager.registerListener(new Listener() {
            @Override
            public void onClosePosition() {
                gridManager.start();
            }
            @Override
            public void onINeedMoney() {
                gridManager.stop();
            }
            @Override
            public void onAbort() {
                gridManager.start();
            }
            @Override
            public void onEndWindow(){
                gridManager.start();
            }
            @Override
            public void onOpenPosition() {
                discordConnector.sendMessage("Posición abierta ya sabes owo");
            }
        });

        environmentManager.getPort().addManager(balanceMaxRiskManager, fundingOnTimeManager);
    }
}
