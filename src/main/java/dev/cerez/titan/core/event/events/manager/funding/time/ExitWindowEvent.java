package dev.cerez.titan.core.event.events.manager.funding.time;

import dev.cerez.titan.core.strategy.funding.time.FundingOnTimeManager;

public final class ExitWindowEvent extends FundingOnTimeEvent {
    public ExitWindowEvent(FundingOnTimeManager eventManager) {
        super(eventManager);
    }
}
