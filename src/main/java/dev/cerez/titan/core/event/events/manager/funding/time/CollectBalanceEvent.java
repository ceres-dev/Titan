package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public final class CollectBalanceEvent extends FundingOnTimeEvent {
    public CollectBalanceEvent(FundingOnTimeManager eventManager) {
        super(eventManager);
    }
}
