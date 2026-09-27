package dev.cerez.titan.command.commands;

import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.strategy.fundingO.FundingOnTimeManager;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.StorageManagerJsonLocal;
import dev.cerez.titan.utils.Provider;
import dev.cerez.titan.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FundingOnTimeCommand extends BaseCommand {
    public FundingOnTimeCommand() {
        super("fundingOnTime", "o");
    }

    @Override
    public void execute(@NotNull List<String> args) {
        StorageManager storage = new StorageManagerJsonLocal(Utils.getRootId());
        BinanceConnector connector = new BinanceConnector(storage.getConfigProvider(BaseConnector.ConnectorConfig.class, "testnet"));
        connector.start();
        FundingOnTimeManager fundingManger = new FundingOnTimeManager(Provider.from(FundingOnTimeManager.FundingMangerConfiguration.builder().build()),connector, new StorageManagerJsonLocal(Utils.getRootId()));
        fundingManger.start();
    }
}
