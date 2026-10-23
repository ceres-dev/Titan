package dev.cerez.titan.core.event.events;

import dev.cerez.titan.core.event.BaseListener;
import dev.cerez.titan.core.strategy.grid.GridManager;

public interface GridManagerListener extends BaseListener {

    default void onUpdate(){}

}
