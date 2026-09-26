package dev.cerez.titan.utils;

import org.jetbrains.annotations.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public enum MarketSession {
    OVERNIGHT,
    PRE_MARKET,
    REGULAR,
    POST_MARKET,
    CLOSED;


    public static MarketSession getSession(@NotNull ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        DayOfWeek day = now.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return MarketSession.CLOSED;
        }

        LocalTime time = now.toLocalTime();

        if (time.isBefore(LocalTime.of(3, 0))) {
            return MarketSession.OVERNIGHT;
        }

        if (time.isBefore(LocalTime.of(8, 30))) {
            return MarketSession.PRE_MARKET;
        }

        if (time.isBefore(LocalTime.of(15, 0))) {
            return MarketSession.REGULAR;
        }

        if (time.isBefore(LocalTime.of(19, 0))) {
            return MarketSession.POST_MARKET;
        }

        return MarketSession.OVERNIGHT;
    }
}
