package dev.cerez.titan.core.strategy.funding.neutral;

import dev.cerez.titan.connector.BaseConnector;
import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.connector.model.BookTick;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Blocking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * @author chatGPT
 */

@RequiredArgsConstructor
public class BlockerForSpread {

    private final BinanceConnector connector;
    private final String symbol;

    @Getter
    private volatile BigDecimal currentSpreadEntry = BigDecimal.ZERO;
    @Getter
    private volatile BigDecimal currentSpreadExit = BigDecimal.ZERO;
    private final LinkedList<BaseConnector.WebSocketSubscribe> responsesWebSocket = new LinkedList<>();

    @Blocking
    public void waitEntrySpred(BigDecimal target) {
        waitSpread(target, true);
    }

    @Blocking
    public void waitExitSpread(BigDecimal target) {
        waitSpread(target, false);
    }

    private void waitSpread(BigDecimal target, boolean entry) {
        final Thread currentThread = Thread.currentThread();

        final AtomicReference<BookTick> futuresTick = new AtomicReference<>();
        final AtomicReference<BookTick> spotTick = new AtomicReference<>();
        final AtomicBoolean reached = new AtomicBoolean(false);

        final Consumer<BookTick> futuresListener = tick -> {
            futuresTick.set(tick);
            checkSpread(
                    futuresTick.get(),
                    spotTick.get(),
                    target,
                    entry,
                    reached,
                    currentThread
            );
        };

        final Consumer<BookTick> spotListener = tick -> {
            spotTick.set(tick);
            checkSpread(
                    futuresTick.get(),
                    spotTick.get(),
                    target,
                    entry,
                    reached,
                    currentThread
            );
        };

        try {
            /*
             * Es importante crear ambas suscripciones antes de bloquear.
             */
            responsesWebSocket.add(connector.wfSubscribeBookTicker(futuresListener, symbol));
            responsesWebSocket.add(connector.wsSubscribeBookTicker(spotListener, symbol));

            /*
             * Puede que el spread ya cumpla el objetivo antes
             * de que park() se ejecute. LockSupport conserva el
             * "permiso" del unpark(), por lo que no se pierde.
             */
            while (!reached.get()) {
                LockSupport.park(this);
            }
        } finally {
            responsesWebSocket.forEach(BaseConnector.WebSocketSubscribe::revoke);
            connector.stop();
        }
    }

    private void checkSpread(
            BookTick futures,
            BookTick spot,
            BigDecimal target,
            boolean entry,
            AtomicBoolean reached,
            Thread thread
    ) {
        if (futures == null || spot == null || reached.get()) {
            return;
        }

        BigDecimal spread;

        if (entry) {
            /*
             * Abrir:
             *
             * Spot  -> SELL -> BID
             * Future -> BUY -> ASK
             *
             * Spread = FutureAsk / SpotBid - 1
             */
            if (spot.bidPrice().signum() <= 0) {
                return;
            }

            spread = futures.askPrice()
                    .divide(spot.bidPrice(), 12, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE);

        } else {
            /*
             * Cerrar:
             *
             * Future -> SELL -> BID
             * Spot   -> BUY  -> ASK
             *
             * Spread = FutureBid / SpotAsk - 1
             */
            if (spot.askPrice().signum() <= 0) {
                return;
            }

            spread = futures.bidPrice()
                    .divide(spot.askPrice(), 12, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE);
        }
        if (entry) {
            setCurrentSpreadEntry(spread);
        }else {
            setCurrentSpreadExit(spread);
        }
        boolean condition = entry
                ? spread.compareTo(target) >= 0
                : spread.compareTo(target) <= 0;

        if (condition && reached.compareAndSet(false, true)) {
            LockSupport.unpark(thread);
        }
    }

    private synchronized void setCurrentSpreadEntry(BigDecimal currentSpreadEntry) {
        this.currentSpreadEntry = currentSpreadEntry;
    }

    private synchronized void setCurrentSpreadExit(BigDecimal currentSpreadExit) {
        this.currentSpreadExit = currentSpreadExit;
    }
}
