package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public final class EntryWidowEvent extends  FundingOnTimeEvent {
    public EntryWidowEvent(FundingOnTimeManager eventManager) {
        super(eventManager);
    }
}
