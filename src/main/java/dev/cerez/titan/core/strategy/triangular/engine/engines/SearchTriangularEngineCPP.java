package dev.cerez.titan.core.strategy.triangular.engine.engines;

import dev.cerez.titan.connector.model.BookTickDouble;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.core.strategy.triangular.engine.SearchTriangularEngine;
import dev.cerez.titan.core.strategy.triangular.engine.model.NameAsset;
import dev.cerez.titan.core.strategy.triangular.utils.TriangularArbitrageOpportunity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchTriangularEngineCPP extends SearchTriangularEngine {
    public SearchTriangularEngineCPP(EngineConfig engineConfig) {
        super(engineConfig);
    }

    @Override
    public void configure(@NotNull Map<String, Symbol> allSymbolMap, @NotNull Map<String, BookTickDouble> liveTickers){

    }

    @Override
    public List<TriangularArbitrageOpportunity> computeTriangularArbitrageOpportunities(@NotNull BookTickDouble updatedTicker) {
        return List.of();
    }

    @Override
    protected void buildGraf(@NotNull Map<String, Symbol> exchangeInfoSpot, @NotNull Map<String, BookTickDouble> liveTickers) {

    }

    @Override
    protected @NotNull Map<NameAsset, ArrayList<ArbitrageEdge>> updateGraf(@NotNull Map<String, Symbol> exchangeInfoSpot, @NotNull BookTickDouble updatedTicker) {
        return Map.of();
    }
}
