package dev.cerez.titan.core.strategy.funding.time;

import com.fasterxml.jackson.databind.JsonNode;
import dev.cerez.titan.Log;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.core.BaseConfig;
import dev.cerez.titan.core.BaseManager;
import dev.cerez.titan.core.PersistenceNope;
import dev.cerez.titan.core.event.events.FundingOnTimeManagerListener;
import dev.cerez.titan.core.strategy.TypeManager;
import dev.cerez.titan.storage.StorageManager;
import dev.cerez.titan.utils.Provider;
import dev.cerez.titan.utils.Utils;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
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

public final class FundingOnTimeManager extends BaseManager<FundingOnTimeManager.FundingMangerConfiguration, PersistenceNope, BinanceConnector, FundingOnTimeManagerListener> {

    private final ConcurrentMap<String, SymbolTarget> symbolsPending = new ConcurrentHashMap<>();

    public FundingOnTimeManager(@NotNull Provider<FundingMangerConfiguration> config, @NonNull BinanceConnector connector, @NotNull StorageManager storageManager) {
        super(config, PersistenceNope.class, connector, storageManager);
    }

    @Override
    protected void internalStart() {
        connector.wuCreateEventAccountUpdate((jsonNode -> {
            JsonNode node = jsonNode.get("a");
            if (node.get("m").asText().equals("FUNDING_FEE")){
                JsonNode nodeSymbol = node.get("S");
                if (nodeSymbol != null){
                    Log.info("Notificación de Funding, de: " + nodeSymbol.asText());
                    SymbolTarget symbolTarget = symbolsPending.remove(nodeSymbol.asText());
                    if (symbolTarget != null) symbolTarget.closePosition();
                }
            }
        }), null, true);
        executor.execute(() -> {
            while (running) {
                Log.info("Starting FundingManger");
                searchFunding();
                LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(5));
            }
        });
    }



    @Override
    protected void internalStop() {
        connector.wuDeleteEventAccountUpdate(null);
    }


    private void searchFunding(){
        var config = getConfig();
        Instant now = Instant.now();
        Instant nextHour = now
                .truncatedTo(ChronoUnit.HOURS)
                .plus(1, ChronoUnit.HOURS);
        Duration remaining = Duration.between(now, nextHour);

        Log.info("Espera del Financiación: %ds", remaining.toSeconds());
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(remaining.toMillis() - TimeUnit.SECONDS.toMillis(60)));

        CompletableFuture<Map<String, BinanceConnector.FundingRate>> fundingFuture = CompletableFuture.supplyAsync(connector::fGetFundingRate);
        CompletableFuture<RangeTime> remoteFuture = CompletableFuture.supplyAsync(() -> getDeltaClockRemote(TimeUnit.SECONDS, 20));
        CompletableFuture<Map<String, Symbol>> symbolsFuture = CompletableFuture.supplyAsync(connector::fGetAllSymbols);

        Map<String, BinanceConnector.FundingRate> funding = fundingFuture.join();
        RangeTime remote = remoteFuture.join();
        Map<String, Symbol> symbols = symbolsFuture.join();
        Map<String, BigDecimal> balanceAvailable = fGetBalance();

        BigDecimal balance = balanceAvailable.get("USDT");
        Log.info("Balance Disponible: %.4f USDT", balance);

        long current = System.currentTimeMillis();

        List<BinanceConnector.FundingRate> targetFiltered  = funding.values().stream()
                .filter(f -> (f.nextFundingTime() - current) < TimeUnit.HOURS.toMillis(1))
                .filter(f -> f.symbol().endsWith("USDT"))
                .filter(f -> f.nextFundingRateAbs().compareTo(new BigDecimal("0.002")) > 0)
                .sorted(Comparator.comparing(BinanceConnector.FundingRate::nextFundingRateAbs))
                .toList();

        Log.info("Symbols Disponible: %s",
                String.join(", ",
                        targetFiltered.stream().map(f ->f.symbol() + "@" + f.nextFundingRate()).toList()
                )
        );

        List<BinanceConnector.FundingRate> targetLimited = targetFiltered.stream().limit(6).toList();

        if (targetLimited.isEmpty()) {
            Log.info("Abort: No funding rate found");
            callEvent(FundingOnTimeManagerListener::onAbort);
        }

        if (targetLimited.size() < 3) {
            Log.info("Abort: Mucha competencia %d", targetLimited.size());
            callEvent(FundingOnTimeManagerListener::onAbort);
            return;
        }

        callEvent(FundingOnTimeManagerListener::onPrepare);

        if (config.getMaxQuantityQuote().compareTo(balance) >= 0){
            Log.info("Abort: Insufficient balance");
            callEvent(FundingOnTimeManagerListener::onAbort);
            return;
        }

        List<SymbolTarget> targets = List.of(targetLimited.stream()
                .map(fundingRate -> new SymbolTarget(config, symbols.get(fundingRate.symbol()), fundingRate))
                .toList()
                .getLast());

        try {
            Log.info("Delta temporal: Avg: %dms, Max: %dms, Min: %dms", remote.avg(), remote.max(), remote.min());
            symbolsPending.clear();
            targets.forEach(symbolTarget -> symbolsPending.put(symbolTarget.getTarget().symbol(), symbolTarget));
            waitForFunding(remote, targets);
        } catch (Exception e) {
            callEvent(FundingOnTimeManagerListener::onAbort);
            Log.warning("Abort: Error: %s", e);
        } finally {
            targets.forEach(SymbolTarget::finish);
        }
    }

    private void waitForFunding(@NotNull RangeTime remote,
                                @NotNull List<SymbolTarget> symbols) {

        long fundingTimeLocal = symbols.getFirst().target.nextFundingTime() - remote.avg();
        var config = getConfig();


        long waitFinal = fundingTimeLocal - config.windowSize;
        long currentTimeFinal = System.currentTimeMillis();
        long deltaWaitFinal = waitFinal - currentTimeFinal;
        executor.schedule(() -> callEvent(FundingOnTimeManagerListener::onEndWindow), deltaWaitFinal + 5_000, TimeUnit.MILLISECONDS);
        if (deltaWaitFinal < 2_000){
            callEvent(FundingOnTimeManagerListener::onAbort);
            Log.warning("Abort: out window final %dms", deltaWaitFinal);
            return;
        }

        symbols.forEach(SymbolTarget::prepare);
        Log.info("Espera final: %.3fs".formatted((waitFinal- currentTimeFinal)/1_000d));

        // Esperar hasta el momento de apertura
        parkUntil(waitFinal);

        symbols.forEach(SymbolTarget::openPosition);
        // Está entre 225 a 250
        parkUntil(fundingTimeLocal/* + 249*/); // 40 no funciona, 60 bien
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

    @Override
    public @NotNull TypeManager getTypeManager() {
        return TypeManager.FUNDING_ON_TIME;
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
    public static class FundingMangerConfiguration extends BaseConfig {
        @Builder.Default private BigDecimal maxQuantityQuote = new BigDecimal("6");
        @Builder.Default private BigDecimal fundingRateMin = BigDecimal.valueOf(0.0025);
        @Builder.Default private boolean sendTrade = false;
        @Builder.Default private long windowSize = 500; // MS
    }

    @Getter
    @RequiredArgsConstructor
    public class SymbolTarget {

        private final FundingMangerConfiguration config;
        private final Symbol symbol;
        private final BinanceConnector.FundingRate target;

        private volatile OrderToClosePosition order;
        private volatile BinanceConnector.BookTick currentBookTick;

        public void prepare(){
            Log.info("Symbol: %s @ %.4f%%", target.symbol(), target.nextFundingRate().multiply(new BigDecimal(100)));
            connector.fSetLeverage(target.symbol(), 2);
            connector.wfCreateBookTicker(bookTick -> this.currentBookTick = bookTick, null, target.symbol());
            this.currentBookTick = connector.fGetBookTick(target.symbol());
        }

        public void finish(){
            executor.schedule(this::closePosition, config.windowSize + TimeUnit.SECONDS.toMillis(5), TimeUnit.MILLISECONDS);
            connector.wfRemoveBookTicker(null, target.symbol());
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
                    2
            );

            if (quantityQuoteMax.compareTo(quantityQuote) <= 0){
                Log.warning("Abort: max Quantity %.4f > %.4f", quantityQuote, quantityQuoteMax);
                callEvent(FundingOnTimeManagerListener::onAbort);
            }

            Log.info("Send order open: %s".formatted(target.symbol()));
            if (config.isSendTrade()) {
                callEvent(FundingOnTimeManagerListener::onOpenPosition);
                connector.fSendOrderToMkt(target.symbol(), sideOpen, quantityBase, null, false);
                this.order = new OrderToClosePosition(target.symbol(), quantityBase, sideClose, System.currentTimeMillis());
            }
        }

        private synchronized void closePosition() {
            if (order == null) return;
            Log.info("Send order close: %s", order.symbol());
            long startTime = System.nanoTime();
            try {
                if (config.isSendTrade())
                    connector.wfSendOrderToMkt(order.symbol(), order.sideOrderToClose(), order.quantity(), null, true)
                            .thenAccept((jsonNode -> {
                                try {
                                    long elapsedNanos = System.nanoTime() - startTime;
                                    Log.info("Order status: %d, Delay: %.2fms (%dms) symbol=%s",
                                            jsonNode.get("status").asInt(),
                                            elapsedNanos/1_000_000f,
                                            System.currentTimeMillis() - order.openDate,
                                            order.symbol()
                                    );
                                    order = null;
                                }catch (NullPointerException e) {
                                    Log.error("Order: %s", jsonNode.toString());
                                }
                            }));
            }catch (Exception e){
                Log.exception(e);
            }finally {
                closePositionCheck();
                callEvent(FundingOnTimeManagerListener::onClosePosition);
            }
        }

        private void closePositionCheck(){
            BinanceConnector.FuturePosition position = connector.fGetPosition(target.symbol());
            if (position == null) {
                Log.info("Cierre de la posición exitoso");
                return;
            }else {
                Log.info("Posición abierta: %s", position);
            }
            SideOrder sideOrder = Utils.toSide(position.sidePosition()).inverse();

            if (config.isSendTrade()) connector.fSendOrderToMkt(target.symbol(),
                    sideOrder,
                    position.quantity(),
                    "close-" + Utils.uuidToBase36(UUID.randomUUID()),
                    true
            );
            // Una pequeña espera para que se actualize la cache
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
            closePositionCheck();
        }
    }

    private record OrderToClosePosition(String symbol, BigDecimal quantity, SideOrder sideOrderToClose, long openDate){}

}
