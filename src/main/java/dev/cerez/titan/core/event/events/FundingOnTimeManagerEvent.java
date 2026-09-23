package dev.cerez.titan.core.event.events;

import dev.cerez.titan.core.event.Event;

public interface FundingOnTimeManagerEvent extends Event {

    default void onPrepare() {}

}
