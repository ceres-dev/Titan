package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.financing.timely.TimelyFinancingManager;

public final class AbortEvent extends FundingOnTimeEvent {
    public AbortEvent(TimelyFinancingManager eventManager) {
        super(eventManager);
    }
}
