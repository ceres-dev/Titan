package dev.cerez.titan.core.event.events.manager;

import dev.cerez.titan.core.Manager;
import dev.cerez.titan.core.StatusManager;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ChangeStatusEvent extends ManagerEvent {

    private final StatusManager statusManager;

    public ChangeStatusEvent(Manager<?, ?> eventManager, StatusManager statusManager) {
        super(eventManager);
        this.statusManager = statusManager;
    }
}
