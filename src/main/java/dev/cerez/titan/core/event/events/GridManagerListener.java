package dev.cerez.titan.core.event.events;

import dev.cerez.titan.core.event.Listener;

public interface GridManagerListener extends Listener {

    default void onUpdate(){}

}
