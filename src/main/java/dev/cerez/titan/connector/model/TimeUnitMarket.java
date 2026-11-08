package dev.cerez.titan.connector.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TimeUnitMarket {
    ONE_MINUTE("1m"),
    FIVE_MINUTE("5m"),
    FIFTEEN_MINUTE("15m"),
    THIRTY_MINUTE("30m"),
    ONE_HOUR("1h"),
    FOUR_HOUR("4h"),
    THREE_HOUR("3h"),
    ONE_DAY("1d"),;

    private final String shortName;

}
