package dev.cerez.titan.command.profilers;

import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.command.ProfilerSetup;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.event.EventHandler;
import dev.cerez.titan.core.event.Listener;
import dev.cerez.titan.core.event.events.manager.funding.time.*;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@ToString
public class GridCommand extends BaseCommand implements ProfilerSetup {
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
                .sideGrid(SideGrid.LONG)
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

        //noinspection unused
        environmentManager.getEventManager().registerEvents(new Listener() {
            @EventHandler
            public void onClosePosition(ClosePositionEvent event) {
                gridManager.start();
            }
            @EventHandler
            public void onOpenPosition(OpenPositionEvent event) {
                discordConnector.sendMessage("Posición abierta ya sabes owo");
            }
            @EventHandler
            public void onCollectBalance(CollectBalanceEvent event) {
                gridManager.stop();
            }
            @EventHandler
            public void onAbort(AbortEvent event) {
                gridManager.start();
            }
            @EventHandler
            public void onEndWindow(EndWindowEvent event) {
                gridManager.start();
            }
        });

        environmentManager.getPort().addManager(balanceMaxRiskManager, fundingOnTimeManager);
    }
}
