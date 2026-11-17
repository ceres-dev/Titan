package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.financing.timely.TimelyFinancingManager;

public final class ClosePositionEvent extends FundingOnTimeEvent {
    public ClosePositionEvent(TimelyFinancingManager eventManager) {
        super(eventManager);
    }
}
