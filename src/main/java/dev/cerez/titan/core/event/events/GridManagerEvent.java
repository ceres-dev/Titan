package dev.cerez.titan.core.event.events;

import dev.cerez.titan.core.event.Event;

public interface GridManagerEvent extends Event {

    default void onUpdate(){}

}
