package dev.cerez.titan.strategy.grid;

import dev.cerez.titan.connector.connectors.BinanceConnector;
import dev.cerez.titan.utils.Switch;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.HashSet;

@RequiredArgsConstructor
public class PriceAlarm implements Switch {

    @Getter
    private boolean running = false;
    private final BinanceConnector connector;
    private final String symbol;
    private final HashSet<Alarm> alarms = new HashSet<>();

    public void addAlarm(boolean forBuy, @NotNull BigDecimal price, @NotNull Runnable amount) {
        alarms.add(new Alarm(forBuy, price, amount));
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        connector.wfCreateBookTicker((bookTick -> {
            for (Alarm alarm : alarms) {
                if (alarm.forBuy){
                    if (bookTick.bidPrice().compareTo(alarm.price) >= 0) {
                        alarms.remove(alarm);
                        alarm.runnable().run();
                    }
                }else {
                    if (bookTick.askPrice().compareTo(alarm.price) <= 0) {
                        alarms.remove(alarm);
                        alarm.runnable().run();
                    }
                }
            }
        }), symbol);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        connector.wfRemoveBookTicker(symbol);
        alarms.clear();
    }

    public void clear() {
        alarms.clear();
    }


    private record Alarm(boolean forBuy, @NotNull BigDecimal price, @NotNull Runnable runnable) {}
}
