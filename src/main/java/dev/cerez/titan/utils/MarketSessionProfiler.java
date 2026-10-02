package dev.cerez.titan.utils;

import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.time.*;
import java.util.*;

public class MarketSessionProfiler {

    private record Session(LocalTime start, LocalTime end, MarketSessionState state, EnumSet<DayOfWeek> days) {}

    @Getter
    private final ZoneId zoneId;
    private final List<Session> sessions;
    private final MarketSessionState defaultState;

    @Contract(pure = true)
    private MarketSessionProfiler(@NotNull Builder builder) {
        this.zoneId = builder.zoneId;
        this.sessions = List.copyOf(builder.sessions);
        this.defaultState = builder.defaultState;
    }

    @Contract("_ -> new")
    public static @NonNull Builder builder(ZoneId zoneId) {
        return new Builder(zoneId);
    }

    /**
     * Obtiene el estado del mercado en este instante.
     */
    public MarketSessionState getState() {
        return getState(Instant.now());
    }

    /**
     * Obtiene el estado del mercado para un Instant.
     */
    public MarketSessionState getState(@NotNull Instant instant) {
        ZonedDateTime dateTime = instant.atZone(zoneId);

        LocalTime time = dateTime.toLocalTime();
        DayOfWeek day = dateTime.getDayOfWeek();

        for (Session session : sessions) {
            if (contains(session, day, time)) {
                return session.state();
            }
        }

        return defaultState;
    }

    /**
     * Obtiene el estado usando directamente una fecha/hora con zona.
     */
    public MarketSessionState getState(@NotNull ZonedDateTime dateTime) {
        return getState(dateTime.toInstant());
    }

    /**
     * Indica si actualmente está abierto.
     */
    public boolean isOpen() {
        return getState() != MarketSessionState.CLOSED;
    }

    /**
     * Indica si el mercado está abierto para un instante concreto.
     */
    public boolean isOpen(Instant instant) {
        return getState(instant) != MarketSessionState.CLOSED;
    }

    private boolean contains(
            @NotNull Session session,
            @NotNull DayOfWeek day,
            @NotNull LocalTime time
    ) {
        LocalTime start = session.start();
        LocalTime end = session.end();

        /*
         * start == end significa 24 horas durante los días indicados.
         */
        if (start.equals(end)) {
            return session.days().contains(day);
        }

        /*
         * Sesión normal:
         *
         * 04:00 -> 09:30
         */
        if (start.isBefore(end)) {
            return session.days().contains(day)
                    && !time.isBefore(start)
                    && time.isBefore(end);
        }

        /*
         * Sesión que cruza medianoche:
         *
         * 20:00 -> 04:00
         *
         * Si es lunes 21:00 -> pertenece al lunes.
         * Si es martes 02:00 -> pertenece al lunes.
         */
        if (session.days().contains(day) && !time.isBefore(start)) {
            return true;
        }

        DayOfWeek previousDay = day.minus(1);

        return session.days().contains(previousDay)
                && time.isBefore(end);
    }

    public static final class Builder {

        private final ZoneId zoneId;
        private final List<Session> sessions = new ArrayList<>();

        private MarketSessionState defaultState = MarketSessionState.CLOSED;

        private Builder(ZoneId zoneId) {
            this.zoneId = Objects.requireNonNull(
                    zoneId,
                    "ZoneId is required"
            );
        }

        /**
         * Añade una sesión.
         * <p>
         * Si no se especifican días, se aplica todos los días.
         */
        public Builder session(
                @NotNull LocalTime start,
                @NotNull LocalTime end,
                @NotNull MarketSessionState state,
                @NotNull DayOfWeek... days
        ) {
            EnumSet<DayOfWeek> daySet;

            if (days == null || days.length == 0) {
                daySet = EnumSet.allOf(DayOfWeek.class);
            } else {
                daySet = EnumSet.noneOf(DayOfWeek.class);
                Collections.addAll(daySet, days);
            }

            if (daySet.isEmpty()) {
                throw new IllegalArgumentException(
                        "At least one day is required"
                );
            }

            sessions.add(
                    new Session(
                            start,
                            end,
                            state,
                            daySet
                    )
            );

            return this;
        }

        /**
         * Estado que se devolverá si ninguna sesión coincide.
         */
        @Contract(value = "_ -> this")
        public Builder defaultState(MarketSessionState state) {
            this.defaultState = state;
            return this;
        }

        @Contract(value = "-> new", pure = true)
        public @NotNull MarketSessionProfiler build() {
            return new MarketSessionProfiler(this);
        }
    }
}
