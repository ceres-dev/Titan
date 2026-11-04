package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public final class ClosePositionEvent extends FundingOnTimeEvent {
    public ClosePositionEvent(FundingOnTimeManager eventManager) {
        super(eventManager);
    }
}
