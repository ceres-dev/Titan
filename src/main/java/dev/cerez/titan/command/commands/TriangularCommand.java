package dev.cerez.titan.command.commands;

import dev.cerez.titan.Log;
import dev.cerez.titan.command.BaseCommand;
import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.strategy.triangular.TriangularManager;
import dev.cerez.titan.strategy.triangular.engine.engines.SearchTriangularEngineJava;
import dev.cerez.titan.strategy.triangular.ExecutorCycles;
import dev.cerez.titan.strategy.triangular.utils.Loader;
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
