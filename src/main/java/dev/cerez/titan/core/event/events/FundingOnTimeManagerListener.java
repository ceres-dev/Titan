package dev.cerez.titan.core.event.events;

import dev.cerez.titan.core.event.Listener;

public interface FundingOnTimeManagerListener extends Listener {

    default void onPrepare() {}

    default void onClosePosition() {}

    default void onEndWindow() {}

    default void onAbort() {}

    default void onOpenPosition() {}

}
