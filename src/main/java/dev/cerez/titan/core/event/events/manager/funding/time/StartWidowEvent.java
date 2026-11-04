package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public final class StartWidowEvent extends  FundingOnTimeEvent {
    public StartWidowEvent(FundingOnTimeManager eventManager) {
        super(eventManager);
    }
}
