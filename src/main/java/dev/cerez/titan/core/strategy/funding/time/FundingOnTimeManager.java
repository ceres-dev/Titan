package dev.cerez.titan.core.strategy.funding.time;

import com.fasterxml.jackson.databind.JsonNode;
import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.model.*;
import dev.cerez.titan.core.StatusManager;
import dev.cerez.titan.core.event.events.manager.funding.time.*;
import dev.cerez.titan.log.Log;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.StrategyManager;
import dev.cerez.titan.core.PersistentNope;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.utils.Utils;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public final class FundingOnTimeManager extends StrategyManager<FundingOnTimeManager.FundingManagerConfig, PersistentNope, BinanceConnector> {

    private final ConcurrentMap<String, SymbolTarget> symbolsPendingForClose = new ConcurrentHashMap<>();

    public FundingOnTimeManager(@NotNull StorageManager storageManager, @NotNull BinanceConnector connector) {
        super(storageManager.getStorageProfiler(FundingManagerConfig.class, PersistentNope.class), connector);
    }

    @Override
    protected void internalStart() {
        subscribeLive.add(connector.wuCreateEventAccountUpdate((jsonNode -> {
            JsonNode node = jsonNode.get("a");
            if (node.get("m").asText().equals("FUNDING_FEE")){
                JsonNode nodeSymbol = node.get("S");
                if (nodeSymbol != null){
                    SymbolTarget symbolTarget = symbolsPendingForClose.remove(nodeSymbol.asText());
                    if (symbolTarget != null) {
                        Log.info("Cierre Automático de Funding, de: " + nodeSymbol.asText());
                        symbolTarget.closePosition();
                    }
                }
            }
        })));
        executor.execute(() -> {
            while (running) {
                searchFunding();
                // Un pequeño margúen de seguridad
                LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(1));
            }
        });
    }

    @Override
    protected void internalStop() {

    }

    private void searchFunding() {
        var config = getConfig();
        Instant now = Instant.now();
        Instant nextHour = now
                .truncatedTo(ChronoUnit.HOURS)
                .plus(1, ChronoUnit.HOURS);
        Duration remaining = Duration.between(now, nextHour);

        Log.info("Espera del Financiación: %ds", remaining.toSeconds());
        setStatus(StatusManager.WAITING);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(remaining.toMillis() - TimeUnit.SECONDS.toMillis(60)));
        setStatus(StatusManager.PREPARING);
        callEvent(new EntryWidowEvent(this));
        CompletableFuture<Map<String, BinanceConnector.FundingRate>> fundingFuture = CompletableFuture.supplyAsync(connector::fGetFundingRate);
        CompletableFuture<RangeTime> remoteFuture = CompletableFuture.supplyAsync(() -> getDeltaClockRemote(TimeUnit.SECONDS, 20));
        CompletableFuture<Map<String, Symbol>> symbolsFuture = CompletableFuture.supplyAsync(connector::fGetAllSymbols);

        Map<String, BinanceConnector.FundingRate> funding = fundingFuture.join();
        RangeTime remote = remoteFuture.join();
        Map<String, Symbol> symbols = symbolsFuture.join();

        long current = System.currentTimeMillis();

        List<String> targetFiltered = funding.values().stream()
                .filter(f -> (f.nextFundingTime() - current) < TimeUnit.HOURS.toMillis(1))
                .filter(f -> f.symbol().endsWith("USDT"))
                .filter(f -> f.nextFundingRateAbs().compareTo(config.getFundingRateMin()) > 0)
                .map(BinanceConnector.FundingRate::symbol)
                .toList();

        Map<String, Volume> volumen = new HashMap<>();
        for (String s : targetFiltered) volumen.put(s, connector.fGetVolumen(s, TimeUnitMarket.ONE_HOUR));

        DecimalFormat decimal = new DecimalFormat("###,###,###.##");

        Log.info("Symbols Disponible: %s",
                String.join(", ",
                        targetFiltered.stream().map(funding::get).map(f -> "%s@(funding=%.5f, volumen=%sUSDT)".formatted(
                                f.symbol(), f.nextFundingRate(), decimal.format(volumen.get(f.symbol()).quoteVolumen()))).toList()
                )
        );

        if (config.maxCompetition != -1 && targetFiltered.size() < config.maxCompetition) {
            Log.info("Abort: Mucha competencia %d", targetFiltered.size());
            callEvent(new AbortEvent(this));
            return;
        }

        List<String> targetLimited = config.maxSymbolsCandidate == -1 ? targetFiltered :
                targetFiltered.stream()
                        .sorted(Comparator.comparing((s) -> volumen.get(s).quoteVolumen()))
                        .limit(config.maxSymbolsCandidate)
                        .toList();

        if (targetLimited.isEmpty()) {
            Log.info("Abort: No funding rate found");
            callEvent(new AbortEvent(this));
            return;
        }



        // Preparación para mover fondo o cancelar otras ordenes
        callEvent(new CollectBalanceEvent(this));
        // Esperar que se actualize la cache
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
        Map<String, BigDecimal> balanceAvailable = fGetBalanceAvailable();

        BigDecimal balance = balanceAvailable.get("USDT");
        Log.info("Balance Disponible: %.4f USDT", balance);

        if (config.getMaxQuantityQuote().compareTo(balance) >= 0){
            Log.info("Abort: Insufficient balance");
            callEvent(new AbortEvent(this));
            return;
        }

        List<SymbolTarget> targets = List.of(
                targetLimited.stream()
                .map(fundingSymbol -> new SymbolTarget(config, symbols.get(fundingSymbol), funding.get(fundingSymbol), false))
                .toList()
                .getLast()
        );
        Log.info("Symbols Aplicados: %s",
                String.join(", ",
                        targets.stream().map(f -> funding.get(f.symbol.name())).map(f -> "%s@(funding=%.5f, volumen=%sUSDT)".formatted(
                                f.symbol(), f.nextFundingRate(), decimal.format(volumen.get(f.symbol()).quoteVolumen()))).toList()
                )
        );
        try {
            Log.info("Delta temporal: Avg: %dms, Max: %dms, Min: %dms", remote.avg(), remote.max(), remote.min());
            symbolsPendingForClose.clear();
            targetLimited.forEach(target -> connector.fSetLeverage(target, config.leverage));

            waitForFunding(remote, targets);
        } catch (Exception e) {
            callEvent(new AbortEvent(this));
            Log.warning("Abort: Error: %s", e);
        } finally {
            targets.forEach(SymbolTarget::finish);
        }
    }

    private void waitForFunding(@NotNull RangeTime remote,
                                @NotNull List<SymbolTarget> symbols) {

        long fundingTimeLocal = symbols.getFirst().target.nextFundingTime() - remote.avg();
        var config = getConfig();

        long waitFinal = fundingTimeLocal - config.offsetOpenPositon;
        long currentTimeFinal = System.currentTimeMillis();
        long deltaWaitFinal = waitFinal - currentTimeFinal;
        executor.schedule(() -> callEvent(new ExitWindowEvent(this)), deltaWaitFinal + 5_000, TimeUnit.MILLISECONDS);
        if (deltaWaitFinal < 2_000){
            callEvent(new AbortEvent(this));
            Log.warning("Abort: out window final %dms", deltaWaitFinal);
            return;
        }

        symbols.forEach(SymbolTarget::prepare);
        Log.info("Espera final: %.3fs".formatted((waitFinal- currentTimeFinal)/1_000d));

        // Esperar hasta el momento de apertura
        parkUntil(waitFinal);

        symbols.forEach(symbolTarget -> symbolsPendingForClose.put(symbolTarget.getTarget().symbol(), symbolTarget));
        symbols.forEach(SymbolTarget::openPosition);
        // Espera hasta la hora exacta
        parkUntil(fundingTimeLocal);
        // Por si tarda mucho la notificación, un cierre manual
        executor.schedule(() -> symbolsPendingForClose.values().removeIf((s) -> {
            try {
                Log.warning("Cierre manual de Funding, de: " + s.symbol);
                s.closePosition();
                s.closePositionCheck();
                return true;
            }catch (Exception e) {
                Log.exception(e);
                return false;
            }
        }), 800,  TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("SameParameterValue")
    private @NotNull RangeTime getDeltaClockRemote(TimeUnit unit, long timeMax) {
        AtomicBoolean running = new AtomicBoolean(true);
        executor.schedule(() -> running.set(false), timeMax, unit);
        LinkedList<Long> deltaTime = new LinkedList<>();
        while (running.get()) {
            long before = System.currentTimeMillis();
            long serverTime = connector.getTimeSever();
            long after = System.currentTimeMillis();
            long midpoint = before + (after - before) / 2;
            deltaTime.add(serverTime - midpoint);
        }
        return newRange(deltaTime);
    }

    private record RangeTime(long max, long min, long avg){}

    @Contract("_ -> new")
    private @NotNull RangeTime newRange(@NonNull List<Long> longs){
        return new RangeTime(
                longs.stream().max(Comparator.comparing(Long::longValue)).orElse(0L),
                longs.stream().min(Comparator.comparing(Long::longValue)).orElse(0L),
                (long) longs.stream().mapToLong(Long::longValue).average().orElse(0D)
        );
    }

    private static void parkUntil(long targetMillis) {
        while (true) {
            long remaining = targetMillis - System.currentTimeMillis();

            if (remaining <= 0) {
                return;
            }

            if (remaining > 2) {
                LockSupport.parkNanos(
                        TimeUnit.MILLISECONDS.toNanos(remaining - 1)
                );
            } else {
                Thread.onSpinWait();
            }
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    public static class FundingManagerConfig extends BaseConfig {
        @Builder.Default private BigDecimal maxQuantityQuote = new BigDecimal("6");
        @Builder.Default private BigDecimal fundingRateMin = BigDecimal.valueOf(0.002);
        @Builder.Default private boolean sendTrade = false;
        @Builder.Default private long offsetOpenPositon = 1000; // MS
        @Builder.Default private int leverage = 1;
        @Builder.Default private int maxCompetition = 3;
        @Builder.Default private int maxSymbolsCandidate = 5;
    }

    @Getter
    @RequiredArgsConstructor
    public class SymbolTarget {

        private final FundingManagerConfig config;
        private final Symbol symbol;
        private final BinanceConnector.FundingRate target;
        private final boolean inverse;

        private volatile OrderToClosePosition order;
        private volatile BookTick currentBookTick;
        private volatile BaseConnector.WebSocketSubscribe subscribeBookTick;

        public void prepare(){
            Log.info("Symbol: %s @ %.4f%%", target.symbol(), target.nextFundingRate().multiply(new BigDecimal(100)));
            connector.fSetLeverage(target.symbol(), 2);
            subscribeBookTick = connector.wfSubscribeBookTicker(bookTick -> this.currentBookTick = bookTick, target.symbol());
            this.currentBookTick = connector.fGetBookTick(target.symbol());
        }

        public void finish(){
            executor.schedule(this::closePosition, TimeUnit.SECONDS.toMillis(5), TimeUnit.MILLISECONDS);
            subscribeBookTick.revoke();
        }

        public void openPosition() {
            SideOrder sideOpen = target.nextFundingRate().signum() > 0 ? SideOrder.SELL : SideOrder.BUY;
            SideOrder sideClose = sideOpen.inverse();
            BigDecimal quantityQuoteMax = config.getMaxQuantityQuote();
            BigDecimal quantityBase =(sideOpen.isBuy()
                    ? /*currentBookTick.askQty().min*/(symbol.realMinNotionalBase(currentBookTick.askPrice()))
                    : /*currentBookTick.bidQty().min*/(symbol.realMinNotionalBase(currentBookTick.bidPrice()))
            ).multiply(new BigDecimal("1.1"));
            BigDecimal quantityQuote = (sideOpen.isBuy()
                    ? symbol.realMinNotionalQuote(currentBookTick.askPrice())
                    : symbol.realMinNotionalQuote(currentBookTick.bidPrice())
            ).multiply(new BigDecimal("1.1"));

            Log.info(
                    "symbol=%s price=%s qty=%s notional=%s leverage=%s",
                    target.symbol(),
                    sideOpen.isBuy() ? currentBookTick.askPrice() : currentBookTick.bidPrice(),
                    quantityBase,
                    quantityQuote,
                    config.leverage
            );

            if (quantityQuoteMax.compareTo(quantityQuote) <= 0){
                Log.warning("Abort: max Quantity %.4f > %.4f", quantityQuote, quantityQuoteMax);
                callEvent(new AbortEvent(FundingOnTimeManager.this));
            }

            Log.info("Send order open: %s".formatted(target.symbol()));
            if (config.isSendTrade()) {
                callEvent(new OpenPositionEvent(FundingOnTimeManager.this));
                connector.fSendOrderToMkt(target.symbol(), sideOpen, quantityBase, null, false);
                this.order = new OrderToClosePosition(target.symbol(), quantityBase, sideClose, System.currentTimeMillis());
                Log.info("Posición Abierta");
            }
        }

        private synchronized void closePosition() {
            if (order == null) return;
            Log.info("Send order close: %s", order.symbol());
            long startTime = System.nanoTime();
            try {
                if (config.isSendTrade())
                    connector.wfSendOrderToMkt(order.symbol(), order.sideOrderToClose(), order.quantity().abs(), null, true)
                            .thenAccept((jsonNode -> {
                                int status = jsonNode.get("status").asInt();
                                try {
                                    long elapsedNanos = System.nanoTime() - startTime;
                                    Log.info("Order status: %d, Delay: %.2fms (%dms) symbol=%s",
                                            status,
                                            elapsedNanos/1_000_000f,
                                            System.currentTimeMillis() - order.openDate,
                                            order.symbol()
                                    );
                                    if (status != 200){
                                        Log.warning("Repuesta: %s", jsonNode);
                                        Log.info("Enviando orden de emergencia");
                                        connector.fSendOrderToMkt(order.symbol(), order.sideOrderToClose(), order.quantity().abs(), null, true);
                                    }
                                    order = null;
                                }catch (Exception e) {
                                    Log.exception(e);
                                }finally {
                                    connector.checkWebSockets();
                                }
                            }));
            }catch (Exception e){
                Log.exception(e);
            }finally {
                closePositionCheck();
            }
        }

        private void closePositionCheck(){
            BinanceConnector.FuturePosition position = connector.fGetPosition(target.symbol());
            if (position == null) {
                Log.info("Cierre de la posición exitoso");
                callEvent(new ClosePositionEvent(FundingOnTimeManager.this));
                return;
            }
            Log.info("Posición abierta: %s", position);
            Log.info("Send order close: %s", target.symbol());
            SideOrder sideOrder = Utils.toSide(position.sidePosition()).inverse();
            AtomicBoolean isOk = new AtomicBoolean(false);
            if (config.isSendTrade()) connector.wfSendOrderToMkt(target.symbol(),
                    sideOrder,
                    position.quantity().abs(),
                    "close-" + Utils.uuidToBase36(UUID.randomUUID()),
                    true
            ).thenAccept((jsonNode -> {
                try {
                    int status = jsonNode.get("status").asInt();
                    if (status != 200){
                        Log.warning("Repuesta: %s", jsonNode);
                        // Si es: ReduceOnly Order is rejected. Esta bien.
                        int code = jsonNode.get("error").get("code").asInt();
                        if (code == -2022){
                            isOk.set(true);
                            return;
                        }
                        Log.info("Enviando orden de emergencia");
                        connector.fSendOrderToMkt(order.symbol(), order.sideOrderToClose(), order.quantity().abs(), null, true);
                    }else isOk.set(true);
                } catch (Exception e) {
                    Log.exception(e);
                } finally {
                    connector.checkWebSockets();
                }
            })).join();
            if (isOk.get()) {
                Log.info("Cierre de la posición exitoso");
                callEvent(new ClosePositionEvent(FundingOnTimeManager.this));
                return;
            }
            // Una pequeña espera para que se actualize la cache
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
            closePositionCheck();
        }
    }

    private record OrderToClosePosition(String symbol, BigDecimal quantity, SideOrder sideOrderToClose, long openDate){}

}
