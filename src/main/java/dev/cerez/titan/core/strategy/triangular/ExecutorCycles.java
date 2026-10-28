package dev.cerez.titan.core.strategy.triangular;

import dev.cerez.titan.connector.Connector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.exception.ApiException;
import dev.cerez.titan.connector.model.OrderResult;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.core.strategy.triangular.engine.SearchTriangularEngine;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.utils.Utils;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ExecutorCycles {

    private volatile SearchTriangularEngine.Opportunity currentOpportunity;
    private volatile CompletableFuture<Object> runLoop = CompletableFuture.completedFuture(new Object());
    private volatile long nanoTimeStartCycles = 0;
    private BigDecimal pnl;

    private final @NotNull DecimalFormat decimalFormat = new DecimalFormat("0.00#######");
    private final @NotNull Set<SearchTriangularEngine.Opportunity> opportunityWindows = new HashSet<>();
    private final @NotNull Executor executor = Executors.newFixedThreadPool(4, Utils.getThreadFactory(this));
    private final @NotNull HashMap<String, Symbol> symbolsByName;
    private final @NotNull Connector connector;
    private final @NotNull ExecutorCycles.ExecutorCyclesConfig config;

    public ExecutorCycles(@NotNull ExecutorCycles.ExecutorCyclesConfig config, @NotNull Connector connector) {
        HashMap<String, Symbol> symbolsByName = new HashMap<>();
        for (Symbol symbolConfigurable : connector.sGetAllSymbols().values()) {
            symbolsByName.put(symbolConfigurable.name(), symbolConfigurable);
        }
        this.connector = connector;
        this.config = config;
        this.symbolsByName = symbolsByName;
    }

    public synchronized void onOpportunities(List<SearchTriangularEngine.@NotNull Opportunity> onOpportunities, long nanotime) {
        HashSet<SearchTriangularEngine.Opportunity> current = new HashSet<>(onOpportunities);

        boolean changed = current.size() != opportunityWindows.size() || !current.containsAll(opportunityWindows);
        if (!changed) {
            return;
        }else {
            opportunityWindows.clear();
            opportunityWindows.addAll(current);
        }

        long opportunityNanoTime = nanotime;
        if (currentOpportunity != null && !current.contains(currentOpportunity)) {
            long currentNanoTime = System.nanoTime();
            Log.info("<red>Cierre del bucle (Termino Ventana). Duración: %.2fms (Computo: %.2fms, Ticks: %d)",
                    (currentNanoTime - nanoTimeStartCycles) / 1_000_000d,
                    (currentNanoTime - opportunityNanoTime) / 1_000_000d,
                    currentOpportunity.lifeTime().ticks()
            );
            currentOpportunity.lifeTime().closed(currentNanoTime);
//            currentOpportunity.lifeTime().(nanoTimeStartCycles);
            this.currentOpportunity = null;
            return;
        }

        SearchTriangularEngine.Opportunity newOpportunity = null;
        for (SearchTriangularEngine.Opportunity opportunity : onOpportunities) {
            if (newOpportunity == null || opportunity.profitPercent() > newOpportunity.profitPercent()) {
                newOpportunity = opportunity;
            }
        }

        if (newOpportunity != null) {
            if (currentOpportunity != null) {
                if (newOpportunity.equals(currentOpportunity)) {
                    return;
                }

                if (this.currentOpportunity == newOpportunity &&
                        newOpportunity.profitPercent() < this.currentOpportunity.profitPercent()
                ) {
                    long currentNanoTime = System.nanoTime();
                    Log.info("<red>Cierre del bucle (El Profit cayo). Duración: %.2fms (Computo: %.2fms)",
                            (currentNanoTime - nanoTimeStartCycles) / 1_000_000d,
                            (currentNanoTime - opportunityNanoTime) / 1_000_000d

                    );
                    currentOpportunity.lifeTime().closed(currentNanoTime);
//                    currentOpportunity.lifeTime().setOpenNanoTime(nanoTimeStartCycles);
                    this.currentOpportunity = null;
                    return;
                }

                if (newOpportunity.profitPercent() < config.getMinProfit() ||
                        newOpportunity.profitPercent() < this.currentOpportunity.profitPercent()){
                    return;
                }
            }
            SearchTriangularEngine.Leg USDT = null;
            for (SearchTriangularEngine.Leg edge : newOpportunity.legs()) {
                if (config.getPreferredStartAsset().equals(edge.from().name())) {
                    USDT = edge;
                    break;
                }
            }
            if (USDT == null) return;
            int index = newOpportunity.legs().indexOf(USDT);
            List<SearchTriangularEngine.Leg> rotatedEdges = new ArrayList<>(newOpportunity.legs());
            if (index != -1) {
                Collections.rotate(rotatedEdges, -index);
            }
            List<String> rotatedAssets = new ArrayList<>(rotatedEdges.size() + 1);
            if (!rotatedEdges.isEmpty()) {
                rotatedAssets.add(rotatedEdges.getFirst().from().name());
                for (SearchTriangularEngine.Leg edge : rotatedEdges) {
                    rotatedAssets.add(edge.to().name());
                }
            }

            if (config.getMaxLag() != -1 &&
                    !config.isUseDelay() &&
                    config.getMaxLag() < (((System.nanoTime() - opportunityNanoTime) / 1_000_000d) - config.getDelay())
            ) {
                return;
            }
            if (currentOpportunity != null) {
                long currentNanoTime = System.nanoTime();
                Log.info("<yellow>Cambio de bucle. Duración: %.2fms (Computo: %.2fms)",
                        (currentNanoTime - this.nanoTimeStartCycles) / 1_000_000d,
                        (currentNanoTime - opportunityNanoTime) / 1_000_000d
                );
            }
            printCycles(newOpportunity, opportunityNanoTime);

            this.nanoTimeStartCycles = System.nanoTime();
            this.currentOpportunity = newOpportunity;//.copyAndSetAssetsCycle(rotatedAssets, rotatedEdges);

            if (config.isUseDelay()){
                CompletableFuture.runAsync(() -> {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(config.getDelay()));
                    tryRunLoop();
                }, executor);
            }else {
                tryRunLoop();
            }
        }
    }

    private synchronized void tryRunLoop() {
        if (runLoop.isDone() && currentOpportunity != null) {
            runLoop = CompletableFuture.supplyAsync(() -> {
                if (config.isTest) {
                    return new Object();
                }
                try {
                    while (currentOpportunity != null) {

                        if (currentOpportunity == null) {
                            return new Object();
                        }
                        List<SearchTriangularEngine.Leg> legs = new ArrayList<>(currentOpportunity.legs());
                        BigDecimal initialBalance = config.getDefaultStartAmount();
                        BigDecimal balance = initialBalance;
                        for (SearchTriangularEngine.Leg leg : legs) {
                            Log.info("Ejecutando: %s %s", leg.symbol(), (leg.side().equals(SideOrder.SELL) ? "<red>SELL" : "<green>BUY") + "<reset>");
                            Symbol symbol = symbolsByName.get(leg.symbol());
                            if (symbol == null) {
                                Log.warning("Símbolo no soportado en este entorno: " + leg.symbol());
                                currentOpportunity = null;
                                break;
                            }
                            String uuid = Utils.uuidToBase36(UUID.randomUUID());
                            try {
                                OrderResult orderResult = connector.sSendOrderToMkt(
                                        symbol.toString(),
                                        leg.side(),
                                        balance,
                                        uuid,
                                        leg.side() == SideOrder.SELL
                                );
                                Log.info(balance + " @ " + leg.from().name() + " -> " + orderResult.executedQty() + " @ " + leg.to().name());

                                balance = orderResult.receivedQty();
                                if (leg.symbol().equals(config.getPreferredStartAsset())) {
                                    pnl = pnl.add(balance.remainder(config.getDefaultStartAmount()));
                                    String balanceString = (balance.compareTo(initialBalance) > 0 ?
                                            "<green>Ganado: " + " +" +decimalFormat.format(balance.remainder(initialBalance)) :
                                            "<red>Perdido: " + " " + decimalFormat.format(balance.remainder(initialBalance))) +
                                            " USDT<reset>";
                                    String pnlString = (pnl.compareTo(BigDecimal.ZERO) > 0 ?
                                            "<green>PNL: " + " +" +decimalFormat.format(pnl) :
                                            "<red>PNL: " + " " + decimalFormat.format(pnl)) +
                                            " USDT<reset>";
                                    Log.info(balanceString + " " + pnlString);
                                }
                                if (balance.compareTo(initialBalance) < 0){
                                    // Terminar Bucle
                                    currentOpportunity = null;
                                }

//                                if (edge.getToAsset().getName().equals("BNB")) {
//                                    balance = Math.max(0.0006, balance - 0.0006);
//                                }
                            }catch (ApiException e) {
                                currentOpportunity = null;
                                Log.exception("Cancelando bucle", e);
                                break;
                            }
                        }
                    }
                }catch (Exception e){
                    Log.exception("Error al ejecutar el bucle", e);
                }
                return new Object();
            }, executor);
        }
    }

    public void printCycles(@NotNull SearchTriangularEngine.Opportunity opportunity, long opportunityNanoTime) {
        long nano = System.nanoTime();
        Log.info(
                "  %s | retorno %.6f | profit %.4f%% | peso %.8f | Lag: %.2fms (%dms+)",
                String.join(" -> ", opportunity.legs().stream().map(SearchTriangularEngine.Leg::symbol).toList()),
                opportunity.netRateProduct(),
                opportunity.profitPercent(),
                -1f,
                (nano - opportunityNanoTime) / 1_000_000d,
                config.isUseDelay() ? config.getDelay() : 0
        );
        for (SearchTriangularEngine.Leg leg : opportunity.legs()) {
            Log.info(
                    "    %s %s via %s @ %.10f -> rate %.10f ",
                    (leg.side().equals(SideOrder.BUY) ? "<green>BUY" : "<red>SELL") + "<reset>",
                    leg.from().name() + "/" + leg.to().name(),
                    leg.symbol(),
                    leg.price(),
                    -1f
            );
        }
    }

    @Builder
    @Getter
    public static class ExecutorCyclesConfig {
        public long delay;
        public boolean useDelay;
        public double minProfit;
        @Builder.Default public boolean isTest = true;
        @Builder.Default public double maxLag = -1;
        @Builder.Default public String preferredStartAsset = "USDT";
        @Builder.Default public BigDecimal defaultStartAmount = new BigDecimal("10");
    }
}
