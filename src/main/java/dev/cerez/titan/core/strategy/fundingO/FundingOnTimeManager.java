package dev.cerez.titan.core.strategy.fundingO;

import dev.cerez.titan.Log;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.model.SideOrder;
import dev.cerez.titan.connector.model.Symbol;
import dev.cerez.titan.core.BaseManager;
import dev.cerez.titan.core.event.events.FundingOnTimeManagerEvent;
import dev.cerez.titan.utils.Config;
import dev.cerez.titan.utils.Utils;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class FundingOnTimeManager extends BaseManager<FundingOnTimeManager.FundingMangerConfig, BinanceConnector, FundingOnTimeManagerEvent> {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4, Utils.getThreadFactory());
    private final BigDecimal fundingRateMin = BigDecimal.valueOf(0.003);
    private volatile BinanceConnector.BookTick currentBookTick = null;

    public FundingOnTimeManager(@NonNull FundingMangerConfig config, @NonNull BinanceConnector connector) {
        super(config, connector);
    }

    @Override
    public void start() {
        if(running)return;
        running = true;
        while (running) {
            Log.info("Starting FundingManger");
            searchFunding();
            LockSupport.parkNanos(TimeUnit.MINUTES.toNanos(5));
        }
    }

    @Override
    public void stop() {
        if(!running)return;
        running = false;
    }

    private final long windowSize = TimeUnit.SECONDS.toMillis(10);

    private void searchFunding(){
        Instant now = Instant.now();
        Instant nextHour = now
                .truncatedTo(ChronoUnit.HOURS)
                .plus(1, ChronoUnit.HOURS);
        Duration remaining = Duration.between(now, nextHour);

        Log.info("Balance: %.4f USDT", connector.fGetBalance().get("USDT"));
        Log.info("waiting for funding seconds:%s", remaining.toSeconds());

        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(remaining.toMillis() - TimeUnit.SECONDS.toMillis(60)));

        if (event != null) event.onPrepare();
        CompletableFuture<Map<String, BinanceConnector.FundingRate>> fundingFuture = CompletableFuture.supplyAsync(connector::fGetFundingRate);
        CompletableFuture<RangeTime> remoteFuture = CompletableFuture.supplyAsync(() -> getDeltaClockRemote(TimeUnit.SECONDS, 20));
        CompletableFuture<Map<String, Symbol>> symbolsFuture = CompletableFuture.supplyAsync(connector::fGetAllSymbols);

        Map<String, BinanceConnector.FundingRate> funding = fundingFuture.join();
        RangeTime remote = remoteFuture.join();
        Map<String, Symbol> symbols = symbolsFuture.join();

        BinanceConnector.FundingRate target = funding.values().stream()
                .filter((f) -> (f.nextFundingTime() - System.currentTimeMillis()) < TimeUnit.HOURS.toMillis(1))
                .max(Comparator.comparing(BinanceConnector.FundingRate::nextFundingRateAbs))
                .orElse(null);

        if (target == null) {
            Log.info("No funding rate found");
            return;
        }

        try {
            connector.wfCreateBookTicker(bookTick -> this.currentBookTick = bookTick, target.symbol());
            this.currentBookTick = connector.fGetBookTick(target.symbol());
            connector.fSetLeverage(target.symbol(), 1);

            if (target.nextFundingRate().abs().compareTo(fundingRateMin) < 0){
                Log.info("Funding rate is out of range %.4f%%", target.nextFundingRate().multiply(new BigDecimal(100)));
                // Elminar el return solo para testnet TODO: volder a poner en real
//            return;
            }
            Log.info("Symbol: %s @ %.4f%%", target.symbol(), target.nextFundingRate().multiply(new BigDecimal(100)));
            waitForFunding(remote, target);
        }finally {
            LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
            closePosition(target.symbol());
            connector.wfRemoveBookTicker(target.symbol());
        }
    }

    private void waitForFunding(@NotNull RangeTime remote,
                                @NotNull BinanceConnector.FundingRate target) {
        long fundingTimeLocal = target.nextFundingTime() - remote.avg();

        long openTime = (fundingTimeLocal - windowSize / 2);

        if (openTime < 500){
            Log.info("Abort: out window", openTime);
            return;
        }

        SideOrder sideOpen = target.nextFundingRate().signum() > 0 ? SideOrder.SELL : SideOrder.BUY;
        SideOrder sideClose = sideOpen.inverse();

        BigDecimal quantityQuote = config.getQuantityQuote();
        BigDecimal quantity = sideOpen.isBuy()
                ? /*currentBookTick.askQty().min*/(quantityQuote.divide(currentBookTick.askPrice(), 12, RoundingMode.DOWN))
                : /*currentBookTick.bidQty().min*/(quantityQuote.divide(currentBookTick.bidPrice(), 12, RoundingMode.DOWN));

        Log.info(
                "symbol=%s price=%s qty=%s notional=%s leverage=%s",
                target.symbol(),
                sideOpen.isBuy() ? currentBookTick.askPrice() : currentBookTick.bidPrice(),
                quantity,
                quantity.multiply(
                        sideOpen.isBuy()
                                ? currentBookTick.askPrice()
                                : currentBookTick.bidPrice()
                ),
                1
        );

        // Esperar hasta el momento de apertura
        parkUntil(openTime);

        Log.info("Send order open: %s %d".formatted(target.symbol(), openTime));
        CompletableFuture.runAsync(() ->
                connector.fSendOrderToMkt(target.symbol(), sideOpen, quantity, null, false)
        );

        // Esperar hasta el funding
        parkUntil(fundingTimeLocal);

        Log.info("Send order close: %s %d".formatted(target.symbol(), fundingTimeLocal));
        CompletableFuture.runAsync(() ->
                connector.fSendOrderToMkt(target.symbol(), sideClose, quantity, null, true)
        );
    }

    private void closePosition(String symbol){
        BinanceConnector.FuturePosition position = connector.fGetPosition(symbol);
        if (position == null) {
            Log.info("Cierre de la posición exitosa");
            return;
        }else {
            Log.info("Posición abierta: %s", position);
        }
        SideOrder sideOrder = Utils.toSide(position.sidePosition()).inverse();

        connector.fSendOrderToMkt(symbol,
                sideOrder,
                position.quantity(),
                "close-" + Utils.uuidToBase36(UUID.randomUUID()),
                true
        );
        closePosition(symbol);
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

    @Builder
    @Data
    public static class FundingMangerConfig implements Config {
        @Builder.Default private BigDecimal quantityQuote = new BigDecimal("20");
    }

}
