package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.financing.timely.TimelyFinancingManager;

public final class EntryWidowEvent extends  FundingOnTimeEvent {
    public EntryWidowEvent(TimelyFinancingManager eventManager) {
        super(eventManager);
    }
}
