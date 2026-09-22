package dev.cerez.titan.core.strategy.funding2;

import dev.cerez.titan.Log;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.utils.BaseManager;
import dev.cerez.titan.utils.Utils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class FundingManger extends BaseManager<FundingManger.FundingMangerConfig, BinanceConnector> {

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4, Utils.getThreadFactory());
    private final BigDecimal fundingRateMin = BigDecimal.valueOf(0.003);

    public FundingManger(@NonNull FundingMangerConfig config, @NonNull BinanceConnector connector) {
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

    private final long windowSize = TimeUnit.SECONDS.toMillis(5);

    private void searchFunding(){
        Instant now = Instant.now();
        Instant nextHour = now
                .truncatedTo(ChronoUnit.HOURS)
                .plus(1, ChronoUnit.HOURS);
        Duration remaining = Duration.between(now, nextHour);

        Log.info("waiting for funding seconds:%s", remaining.toSeconds());

        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(remaining.toMillis() - TimeUnit.SECONDS.toMillis(30)));

        CompletableFuture<Map<String, BinanceConnector.FundingRate>> fundingFuture = CompletableFuture.supplyAsync(connector::fGetFundingRate);
        CompletableFuture<RangeTime> remoteFuture = CompletableFuture.supplyAsync(() -> getDeltaClockRemote(TimeUnit.SECONDS, 20));

        Map<String, BinanceConnector.FundingRate> funding = fundingFuture.join();
        RangeTime remote = remoteFuture.join();

        BinanceConnector.FundingRate target = funding.values().stream()
                .filter((f) -> (f.nextFundingTime() - System.currentTimeMillis()) < TimeUnit.HOURS.toMillis(1))
                .max(Comparator.comparing(BinanceConnector.FundingRate::nextFundingRateAbs))
                .orElse(null);
        if (target == null) {
            Log.info("No funding rate found");
            return;
        }
        if (target.nextFundingRate().abs().compareTo(fundingRateMin) < 0){
            Log.info("Funding rate is out of range");
            return;
        }
        waitForFunding(remote, target);
    }

    private void waitForFunding(@NotNull RangeTime remote,
                                @NotNull BinanceConnector.FundingRate fundingRate) {
        long fundingTimeLocal = fundingRate.nextFundingTime() - remote.avg();

        long openTime = (fundingTimeLocal - windowSize / 2);

        // Esperar hasta el momento de apertura
        parkUntil(openTime);

        Log.info("Send order open: %s %d".formatted(fundingRate.symbol(), openTime));

        // Esperar hasta el funding
        parkUntil(fundingTimeLocal);

        Log.info("Send order close: %s %d".formatted(fundingRate.symbol(), fundingTimeLocal));
    }


    @SuppressWarnings("SameParameterValue")
    @Contract("_, _ -> new")
    private @NotNull FundingManger.RangeTime getDeltaPing(TimeUnit unit, long timeMax){
        AtomicBoolean running = new AtomicBoolean(true);
        executor.schedule(() -> running.set(false), timeMax, unit);
        LinkedList<Long> pingDelta = new LinkedList<>();
        while (running.get()) {
            long delta = connector.fPing();
            pingDelta.add(delta);
        }
        List<Long> pingMs = pingDelta.stream().map(TimeUnit.NANOSECONDS::toMicros).toList();
        return newRange(pingMs);
    }

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

    public static class FundingMangerConfig {}

}
