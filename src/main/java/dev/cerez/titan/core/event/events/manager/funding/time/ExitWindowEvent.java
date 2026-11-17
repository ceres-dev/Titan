package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.financing.timely.TimelyFinancingManager;

public final class ExitWindowEvent extends FundingOnTimeEvent {
    public ExitWindowEvent(TimelyFinancingManager eventManager) {
        super(eventManager);
    }
}
