package dev.cerez.titan.command.profilers;

import dev.cerez.titan.command.Arguments;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.command.ProfilerSetup;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.core.strategy.financing.timely.TimelyFinancingManager;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.storages.StorageManagerMemory;
import dev.cerez.titan.utils.Utils;
import org.jetbrains.annotations.NotNull;

public class FundingOnTimeCommand extends BaseCommand implements ProfilerSetup {
    public FundingOnTimeCommand(EnvironmentManager environmentManager) {
        super("fundingOnTime", "o", environmentManager);
    }

    @Override
    public void execute(@NotNull Arguments args) {
        BinanceConnector connector = this.getEnvironmentManager().getConnector();
        StorageManager storageMemory = new StorageManagerMemory(Utils.getRootId());

        connector.wuCreateEventAccountUpdate((jsonNode -> {
            Log.info(jsonNode.toPrettyString());
        }));
        connector.wuSubscribeEventOrderTradeUpdate((a) -> {
            Log.info(a.toString());
        });
        TimelyFinancingManager.FundingManagerConfig config = TimelyFinancingManager.FundingManagerConfig.builder()
                .sendTrade(true)
                .build();
        TimelyFinancingManager fundingManger = new TimelyFinancingManager(storageMemory.saveIfNotExits(config), connector);
        fundingManger.start();
    }
}
