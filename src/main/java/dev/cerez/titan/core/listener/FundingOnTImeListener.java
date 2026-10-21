package dev.cerez.titan.core.listener;

import dev.cerez.titan.core.event.events.FundingOnTimeManagerListener;
import dev.cerez.titan.core.managers.EnvironmentManager;
import dev.cerez.titan.core.strategy.grid.GridManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FundingOnTImeListener implements FundingOnTimeManagerListener {

    private final EnvironmentManager environment;

    public void onPrepare(){
        environment.getManagerMap().values().stream()
                .filter(m -> m instanceof GridManager)
                .map(m -> (GridManager)m)
                .forEach(GridManager::stop);
    }

    public void onClosePosition(){
        environment.getManagerMap().values().stream()
                .filter(m -> m instanceof GridManager)
                .map(m -> (GridManager)m)
                .forEach(GridManager::start);
    }
}
