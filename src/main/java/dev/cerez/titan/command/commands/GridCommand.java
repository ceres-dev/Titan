package dev.cerez.titan.command.commands;

import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.event.events.FundingOnTimeManagerListener;
import dev.cerez.titan.core.strategy.BalanceRiskManager;
import dev.cerez.titan.core.strategy.fundingO.FundingOnTimeManager;
import dev.cerez.titan.discord.DiscordConnector;
import dev.cerez.titan.core.strategy.grid.GridManager;
import dev.cerez.titan.core.strategy.grid.model.SideGrid;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.StorageManagerJsonLocal;
import dev.cerez.titan.utils.Provider;
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

        StorageManager storageManager = new StorageManagerJsonLocal(Utils.getRootId());
        BinanceConnector binanceConnector = new BinanceConnector(storageManager.getProviderOrSaveConfig(BaseConnector.ConnectorConfig.builder().build()));
        binanceConnector.start();

        GridManager.GridManagerConfiguration gridConfig = GridManager.GridManagerConfiguration.builder()
                .baseAsset("SPY")
                .quoteAsset("USDT")
                .stepSizeHighActivity(new BigDecimal("0.8"))
                .stepSizeMediumActivity(new BigDecimal("0.5"))
                .stepSizeLowActivity(new BigDecimal("0.4"))
                .sizePerOrderBaseAsset(new BigDecimal("0.01"))
                .leverage(5)
                .logsEndPoints(false)
                .sideGrid(SideGrid.LONG)
                .amountPriceOffset(15)
                .build();
        GridManager gridManager = new GridManager(Provider.from(gridConfig), binanceConnector, storageManager);
        gridManager.setBalanceRiskManager(new BalanceRiskManager(binanceConnector, Map.of("SPY", BigDecimal.ONE)));
        gridManager.setName("SPY");
        gridManager.start();

        FundingOnTimeManager.FundingMangerConfiguration fundingConfig = FundingOnTimeManager.FundingMangerConfiguration.builder()
                .sendTrade(true)
                .build();
        FundingOnTimeManager fundingOnTimeManager = new FundingOnTimeManager(Provider.from(fundingConfig), binanceConnector, storageManager);
        fundingOnTimeManager.setName("Funding");
        fundingOnTimeManager.start();

        DiscordConnector discordConnector = new DiscordConnector(storageManager.getProviderOrSaveConfig(DiscordConnector.DiscordConfig.builder().build()));
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
