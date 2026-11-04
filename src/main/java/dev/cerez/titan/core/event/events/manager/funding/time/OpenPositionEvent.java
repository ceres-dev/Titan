package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public final class OpenPositionEvent extends FundingOnTimeEvent {
    public OpenPositionEvent(FundingOnTimeManager eventManager) {
        super(eventManager);
    }
}
