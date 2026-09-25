package dev.cerez.titan.core.environment;

import dev.cerez.titan.core.event.events.FundingOnTimeManagerListener;
import dev.cerez.titan.core.strategy.grid.GridManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FundingOnTImeListener implements FundingOnTimeManagerListener {

    private final EnvironmentManager environment;

    public void onPrepare(){
        environment.getManager().values().stream()
                .filter(m -> m instanceof GridManager)
                .map(m -> (GridManager)m)
                .forEach(GridManager::stop);
    }

    public void onPostExecute(){
        environment.getManager().values().stream()
                .filter(m -> m instanceof GridManager)
                .map(m -> (GridManager)m)
                .forEach(GridManager::start);
    }
}
