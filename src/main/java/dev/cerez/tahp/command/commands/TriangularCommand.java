package dev.cerez.tahp.command.commands;

import dev.cerez.tahp.Log;
import dev.cerez.tahp.command.BaseCommand;
import dev.cerez.tahp.connector.Connector;
import dev.cerez.tahp.connector.connectors.BinanceConnector;
import dev.cerez.tahp.triangular.TriangularManager;
import dev.cerez.tahp.triangular.engine.SearchTriangularEngine;
import dev.cerez.tahp.triangular.engine.engines.SearchTriangularEngineJava;
import dev.cerez.tahp.triangular.ExecutorCycles;
import dev.cerez.tahp.triangular.utils.Loader;
import dev.cerez.tahp.utils.telemtry.Telemetry;
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
        long startTime = System.currentTimeMillis();
        ExecutorCycles.ExecutorCyclesConfig configExecutor = ExecutorCycles.ExecutorCyclesConfig.builder()
                .maxLag(20L)
                .minProfit(0.1d)
                .isTest(true)
                .build();
        Telemetry.TelemetryConfig telemetryConfig = Telemetry.TelemetryConfig.builder()
                .maxDelaysDeltaComputeNanoTime(500)
                .stepsAddDelayComputeNanoTime(10)
                .build();
        TriangularManager.TriangularManagerConfig triangularManagerConfig = TriangularManager.TriangularManagerConfig.builder()
                .maxSymbols(900)
                .banAssets(Set.of("TRY"))
                .maxCycleLength(4)
                .minCycleLength(3)
                .engine(SearchTriangularEngineJava.class)
                .build();

        Connector connector =           new BinanceConnector();
        Telemetry telemetry =           new Telemetry(telemetryConfig);
        Loader loader =                 new Loader();
        ExecutorCycles executorCycles = new ExecutorCycles(configExecutor, connector);

        connector.setTelemetry(telemetry);
        new TriangularManager(triangularManagerConfig, connector, executorCycles::onOpportunities).setTelemetry(telemetry).start();
        Log.info("<green>Ready! %.2fs", (System.currentTimeMillis() - startTime)/1000d);
        loader.printLoader(telemetry);
    }
}
