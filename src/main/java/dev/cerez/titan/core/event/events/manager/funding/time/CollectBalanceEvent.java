package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.financing.timely.TimelyFinancingManager;

public final class CollectBalanceEvent extends FundingOnTimeEvent {
    public CollectBalanceEvent(TimelyFinancingManager eventManager) {
        super(eventManager);
    }
}
