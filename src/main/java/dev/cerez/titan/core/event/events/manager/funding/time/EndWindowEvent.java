package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public final class EndWindowEvent extends FundingOnTimeEvent {
    public EndWindowEvent(FundingOnTimeManager eventManager) {
        super(eventManager);
    }
}
