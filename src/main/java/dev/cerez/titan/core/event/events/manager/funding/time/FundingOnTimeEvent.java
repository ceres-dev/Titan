package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.event.events.manager.ManagerEvent;
import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public abstract class FundingOnTimeEvent extends ManagerEvent {
    public FundingOnTimeEvent(FundingOnTimeManager eventManager) {
        super(eventManager);
    }
}
