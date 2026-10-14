package dev.cerez.titan.core.strategy.grid;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.utils.Switch;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.*;

@RequiredArgsConstructor
public class PriceAlarm implements Switch {

    @Getter
    private boolean running = false;
    private final BinanceConnector connector;
    private final String symbol;
    private final Set<Alarm> alarms = Collections.synchronizedSet(new HashSet<>());

    public void addAlarm(boolean forBuy, @NotNull BigDecimal price, @NotNull Runnable runnable) {
        synchronized (alarms) {
            boolean exists = alarms.stream().anyMatch(alarm ->
                    alarm.forBuy() == forBuy && alarm.price().compareTo(price) == 0
            );

            if (!exists) alarms.add(new Alarm(forBuy, price, runnable));
        }
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        connector.wfCreateBookTicker((bookTick -> {
            List<Alarm> triggered = new ArrayList<>(12);
            synchronized (alarms) {
                for (Alarm alarm : alarms) {
                    boolean reached = alarm.forBuy()
                            ? bookTick.bidPrice().compareTo(alarm.price()) >= 0
                            : bookTick.askPrice().compareTo(alarm.price()) <= 0;

                    if (reached) {
                        triggered.add(alarm);
                    }
                }

                triggered.forEach(alarms::remove);
            }
            for (Alarm alarm : triggered) {
                alarm.runnable().run();
            }
        }), null, symbol);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        connector.wfRemoveBookTicker(null, symbol);
        alarms.clear();
    }

    public void clear() {
        synchronized (alarms) {
            alarms.clear();
        }
    }

    public boolean isEmpty() {
        synchronized (alarms) {
            return alarms.isEmpty();
        }
    }

    private record Alarm(boolean forBuy, @NotNull BigDecimal price, @NotNull Runnable runnable) {}
}
