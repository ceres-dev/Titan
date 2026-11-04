package dev.cerez.titan.core.event.events.manager;

import dev.cerez.titan.core.Manager;
import dev.cerez.titan.core.event.EventManager;
import dev.cerez.titan.core.event.events.Event;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
public abstract class ManagerEvent extends Event {

    private final Manager<?, ?> eventManager;
}
