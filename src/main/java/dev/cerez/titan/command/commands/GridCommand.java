package dev.cerez.titan.command.commands;

import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.event.events.FundingOnTimeManagerListener;
import dev.cerez.titan.core.managers.BalanceMaxRiskManager;
import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
import dev.cerez.titan.storage.storages.StorageManagerJsonLocal;
import dev.cerez.titan.storage.storages.StorageManagerMemory;
import dev.cerez.titan.utils.Utils;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@ToString
public class GridCommand extends BaseCommand {
    public GridCommand() {
        super("grid", "g");
    }

    @Override
    public void execute(@NotNull List<String> args) {

        StorageManagerJsonLocal storageManagerJsonLocal = new StorageManagerJsonLocal(Utils.getRootId());
        StorageManagerMemory storageManagerMemory = new StorageManagerMemory(Utils.getRootId());
        BinanceConnector binanceConnector = new BinanceConnector(storageManagerJsonLocal.saveIfNotExits(BaseConnector.ConnectorConfig.builder().build()));
        binanceConnector.start();

        BalanceMaxRiskManager.BalanceMaxRiskManagerConfig balanceMaxRiskManagerConfig = BalanceMaxRiskManager.BalanceMaxRiskManagerConfig.builder()
                .balanceDivision(Map.of("SPY", BigDecimal.ONE))
                .build();

        BalanceMaxRiskManager balanceMaxRiskManager = new BalanceMaxRiskManager(storageManagerMemory.saveConfig(balanceMaxRiskManagerConfig), binanceConnector);

        GridManager.GridManagerConfiguration gridConfig = GridManager.GridManagerConfiguration.builder()
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
        GridManager gridManager = new GridManager(storageManagerMemory.saveConfig(gridConfig), binanceConnector);
        gridManager.setBalanceMaxRiskManager(balanceMaxRiskManager);
        gridManager.setName("SPY");
        gridManager.start();

        FundingOnTimeManager.FundingManagerConfig fundingConfig = FundingOnTimeManager.FundingManagerConfig.builder()
                .sendTrade(true)
                .build();
        FundingOnTimeManager fundingOnTimeManager = new FundingOnTimeManager(storageManagerMemory.saveConfig(fundingConfig), binanceConnector);
        fundingOnTimeManager.setName("Funding");
        fundingOnTimeManager.start();

        DiscordConnector discordConnector = new DiscordConnector(storageManagerJsonLocal.saveConfig(DiscordConnector.DiscordConfig.builder().build()));
        discordConnector.setStatusProfiler(gridManager);
        discordConnector.start();

        fundingOnTimeManager.registerListener(new FundingOnTimeManagerListener() {
            @Override
            public void onPrepare() {
                gridManager.stop();
            }
            @Override
            public void onClosePosition() {
                gridManager.start();
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


    }
}
