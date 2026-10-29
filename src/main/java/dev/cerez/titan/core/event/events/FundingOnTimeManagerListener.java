package dev.cerez.titan.core.event.events;

import dev.cerez.titan.core.event.BaseListener;
import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public interface FundingOnTimeManagerListener extends BaseListener {

    default void onPrepare() {}

    default void onINeedMoney() {}

    default void onClosePosition() {}

    default void onEndWindow() {}

    default void onAbort() {}

    default void onOpenPosition() {}

}
