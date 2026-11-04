package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public final class AbortEvent extends FundingOnTimeEvent {
    public AbortEvent(FundingOnTimeManager eventManager) {
        super(eventManager);
    }
}
