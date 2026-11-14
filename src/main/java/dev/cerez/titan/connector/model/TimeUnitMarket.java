package dev.cerez.titan.connector.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.TimeUnit;

@Getter
@RequiredArgsConstructor
public enum TimeUnitMarket {
    ONE_SECOND("1s", TimeUnit.SECONDS.toMillis(1)),
    ONE_MINUTE("1m", TimeUnit.MINUTES.toMillis(1)),
    FIVE_MINUTE("5m", TimeUnit.MINUTES.toMillis(5)),
    FIFTEEN_MINUTE("15m", TimeUnit.MINUTES.toMillis(15)),
    THIRTY_MINUTE("30m",  TimeUnit.MINUTES.toMillis(30)),
    ONE_HOUR("1h", TimeUnit.HOURS.toMillis(1)),
    THREE_HOUR("3h", TimeUnit.HOURS.toMillis(3)),
    FOUR_HOUR("4h", TimeUnit.HOURS.toMillis(4)),
    ONE_DAY("1d",  TimeUnit.DAYS.toMillis(1)),;

    private final String shortName;
    private final long duration;

}
