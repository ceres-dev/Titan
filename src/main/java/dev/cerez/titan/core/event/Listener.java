package dev.cerez.titan.core.event;

import dev.cerez.titan.core.event.events.*;

public interface Listener extends
        EnvironmentManagerListener,
        FundingNeutralManagerListener,
        FundingOnTimeManagerListener,
        GridManagerListener,
        TriangularManagerListener
{
}
