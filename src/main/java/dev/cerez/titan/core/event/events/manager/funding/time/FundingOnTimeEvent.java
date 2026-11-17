package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.event.events.manager.ManagerEvent;
import dev.cerez.titan.core.strategy.financing.timely.TimelyFinancingManager;

public abstract class FundingOnTimeEvent extends ManagerEvent {
    public FundingOnTimeEvent(TimelyFinancingManager eventManager) {
        super(eventManager);
    }
}
