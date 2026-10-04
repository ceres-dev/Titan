package dev.cerez.titan.utils;

import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

public enum MarketSession {
    NEW_YORK_STOCK_EXCHANGE(
            MarketSessionProfiler.builder(ZoneId.of("UTC-5"))
                    .session(
                            LocalTime.of(3, 0),
                            LocalTime.of(8, 30),
                            MarketSessionState.PRE_MARKET,
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY
                    )
                    .session(
                            LocalTime.of(8, 30),
                            LocalTime.of(15, 0),
                            MarketSessionState.REGULAR,
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY
                    )
                    .session(
                            LocalTime.of(15, 0),
                            LocalTime.of(19, 0),
                            MarketSessionState.POST_MARKET,
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY,
                            DayOfWeek.THURSDAY,
                            DayOfWeek.FRIDAY
                    )
                    .session(
                            LocalTime.of(19, 0),
                            LocalTime.of(3, 0),
                            MarketSessionState.OVERNIGHT,
                            DayOfWeek.SUNDAY,
                            DayOfWeek.MONDAY,
                            DayOfWeek.TUESDAY,
                            DayOfWeek.WEDNESDAY
                            // El viernes no hay Noche, pasa directo a close
                    )
                    .build(),
            "SPY", "QQQ"
    );


    private final MarketSessionProfiler profiler;
    @Getter
    private final Set<String> assets;

    MarketSession(MarketSessionProfiler profiler, String... assets) {
        this.profiler = profiler;
        this.assets = Set.of(assets);
    }

    public static MarketSessionState of(String asset) {
        for (MarketSession marketSession : MarketSession.values()) {
            if (marketSession.assets.contains(asset)){
                return marketSession.profiler.getState();
            }
        }
        return MarketSessionState.CLOSED;
    }
}
