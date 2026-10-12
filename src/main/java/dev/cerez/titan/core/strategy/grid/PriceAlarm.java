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
        synchronized (this.alarms) {
            alarms.add(new Alarm(forBuy, price, runnable));
        }
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        connector.wfCreateBookTicker((bookTick -> {
            synchronized (alarms){
                LinkedList<Alarm> alarmsForRemove = new LinkedList<>();
                for (Alarm alarm : List.copyOf(alarms)) {
                    if (alarm.forBuy){
                        if (bookTick.bidPrice().compareTo(alarm.price) > 0) {
                            alarmsForRemove.add(alarm);
                            alarm.runnable().run();
                        }
                    }else {
                        if (bookTick.askPrice().compareTo(alarm.price) < 0) {
                            alarmsForRemove.add(alarm);
                            alarm.runnable().run();
                        }
                    }
                }
                alarmsForRemove.forEach(alarms::remove);
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
        alarms.clear();
    }


    private record Alarm(boolean forBuy, @NotNull BigDecimal price, @NotNull Runnable runnable) {}
}
