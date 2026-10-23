package dev.cerez.titan.command.commands;

import dev.cerez.titan.log.Log;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.core.strategy.triangular.TriangularManager;
import dev.cerez.titan.core.strategy.triangular.engine.engines.SearchTriangularEngineJava;
import dev.cerez.titan.core.strategy.triangular.ExecutorCycles;
import dev.cerez.titan.core.strategy.triangular.utils.Loader;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.storages.StorageManagerJsonLocal;
import dev.cerez.titan.storage.storages.StorageManagerMemory;
import dev.cerez.titan.utils.Utils;
import dev.cerez.titan.utils.telemtry.TelemetryCollector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class TriangularCommand extends BaseCommand {
    public TriangularCommand(EnvironmentManager environmentManager) {
        super("triangular", "t", environmentManager);
    }

    @Override
    public void execute(@NotNull List<String> args) {
        Log.info("Starting...");

        ExecutorCycles.ExecutorCyclesConfig configExecutor = ExecutorCycles.ExecutorCyclesConfig.builder()
                .maxLag(20L)
                .minProfit(0.1d)
                .isTest(true)
                .build();
        TelemetryCollector.TelemetryConfig telemetryConfig = TelemetryCollector.TelemetryConfig.builder()
                .maxDelaysDeltaComputeNanoTime(500)
                .stepsAddDelayComputeNanoTime(10)
                .build();
        TriangularManager.TriangularManagerConfiguration triangularManagerConfig = TriangularManager.TriangularManagerConfiguration.builder()
                .maxSymbolsHighVolumen(900)
                .maxSymbolsLowVolumen(90)
                .banAssets(Set.of("TRY"))
                .maxCycleLength(3)
                .minCycleLength(3)
                .engine(SearchTriangularEngineJava.class)
                .build();

        TelemetryCollector telemetry =           new TelemetryCollector(telemetryConfig);
        Loader loader =                 new Loader();
        StorageManager storageManager = new StorageManagerMemory(Utils.getRootId());
        StorageManagerJsonLocal storageManagerJsonLocal = new StorageManagerJsonLocal(Utils.getRootId());
        storageManagerJsonLocal.setConfigProfilerName(BaseConnector.ConnectorConfig.class, "triangular");
        Connector connector =           new BinanceConnector(storageManagerJsonLocal);
        ExecutorCycles executorCycles = new ExecutorCycles(configExecutor, connector);

        connector.setTelemetry(telemetry);
        connector.start();
        storageManager.saveIfNotExits(triangularManagerConfig);
        TriangularManager manager = new TriangularManager(storageManager, connector);
        manager.setTelemetry(telemetry);
        manager.setOnOpportunities(executorCycles::onOpportunities);
        manager.start();
        loader.printLoader(telemetry);
    }
}
