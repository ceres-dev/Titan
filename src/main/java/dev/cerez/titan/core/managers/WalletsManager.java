package dev.cerez.titan.core.managers;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.BaseManager;
import dev.cerez.titan.core.ConfigNope;
import dev.cerez.titan.core.PersistentNope;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.storage.StorageManager;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class WalletsManager extends BaseManager<ConfigNope, PersistentNope, BinanceConnector> {

    protected WalletsManager(@NotNull StorageManager storageManager, @NonNull BinanceConnector connector) {
        super(storageManager.createProfilerManager(ConfigNope.class, PersistentNope.class), connector, TypeManager.WALLETS);
    }

    @Override
    protected void internalStart() {

    }

    @Override
    protected void internalStop() {

    }
}
