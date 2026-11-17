package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.financing.timely.TimelyFinancingManager;

public final class OpenPositionEvent extends FundingOnTimeEvent {
    public OpenPositionEvent(TimelyFinancingManager eventManager) {
        super(eventManager);
    }
}
