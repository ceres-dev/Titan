package dev.cerez.titan.command.commands;

import dev.cerez.titan.Log;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.strategy.triangular.TriangularManager;
import dev.cerez.titan.core.strategy.triangular.engine.engines.SearchTriangularEngineJava;
import dev.cerez.titan.core.strategy.triangular.ExecutorCycles;
import dev.cerez.titan.core.strategy.triangular.utils.Loader;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.storage.StorageManagerJsonLocal;
import dev.cerez.titan.utils.Provider;
import dev.cerez.titan.utils.Utils;
import dev.cerez.titan.utils.telemtry.Telemetry;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class TriangularCommand extends BaseCommand {
    public TriangularCommand() {
        super("triangular", "t");
    }

    @Override
    public void execute(@NotNull List<String> args) {
        Log.info("Starting...");

        ExecutorCycles.ExecutorCyclesConfig configExecutor = ExecutorCycles.ExecutorCyclesConfig.builder()
                .maxLag(20L)
                .minProfit(0.1d)
                .isTest(true)
                .build();
        Telemetry.TelemetryConfig telemetryConfig = Telemetry.TelemetryConfig.builder()
                .maxDelaysDeltaComputeNanoTime(500)
                .stepsAddDelayComputeNanoTime(10)
                .build();
        TriangularManager.TriangularManagerConfiguration triangularManagerConfig = TriangularManager.TriangularManagerConfiguration.builder()
                .maxSymbolsHighVolumen(100)
                .maxSymbolsLowVolumen(800)
                .banAssets(Set.of("TRY"))
                .maxCycleLength(3)
                .minCycleLength(3)
                .engine(SearchTriangularEngineJava.class)
                .build();



        Telemetry telemetry =           new Telemetry(telemetryConfig);
        Loader loader =                 new Loader();
        StorageManager storageManager = new StorageManagerJsonLocal(Utils.getRootId());
        Connector connector =           new BinanceConnector(storageManager.getConfigProvider(BaseConnector.ConnectorConfig.class, null));
        ExecutorCycles executorCycles = new ExecutorCycles(configExecutor, connector);

        connector.setTelemetry(telemetry);
        connector.start();
        TriangularManager manager = new TriangularManager(Provider.from(triangularManagerConfig), connector, storageManager);
        manager.setTelemetry(telemetry);
        manager.setOnOpportunities(executorCycles::onOpportunities);
        manager.start();
        loader.printLoader(telemetry);
    }
}
