package dev.cerez.titan.command.commands;

import dev.cerez.titan.log.Log;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.storages.StorageManagerJsonLocal;
import dev.cerez.titan.storage.storages.StorageManagerMemory;
import dev.cerez.titan.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FundingOnTimeCommand extends BaseCommand {
    public FundingOnTimeCommand(EnvironmentManager environmentManager) {
        super("fundingOnTime", "o", environmentManager);
    }

    @Override
    public void execute(@NotNull List<String> args) {
        StorageManager storageReal = new StorageManagerJsonLocal(Utils.getRootId());
        StorageManager storageMemory = new StorageManagerMemory(Utils.getRootId());
        storageReal.setConfigProfilerName(BaseConnector.ConnectorConfig.class, "testnet");
        BinanceConnector connector = new BinanceConnector(storageReal);
        connector.start();
        connector.wuCreateEventAccountUpdate((jsonNode -> {
            Log.info(jsonNode.toPrettyString());
        }), null);
        connector.wuCreateEventOrderTradeUpdate((a) -> {
            Log.info(a.toString());
        }, null);
        FundingOnTimeManager.FundingManagerConfig config = FundingOnTimeManager.FundingManagerConfig.builder()
                .sendTrade(true)
                .build();
        FundingOnTimeManager fundingManger = new FundingOnTimeManager(storageMemory.saveIfNotExits(config), connector);
        fundingManger.start();
    }
}
