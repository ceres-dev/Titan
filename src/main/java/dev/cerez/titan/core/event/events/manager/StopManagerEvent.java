package dev.cerez.titan.core.event.events.manager;

import dev.cerez.titan.core.Manager;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public final class StopManagerEvent extends ManagerEvent {
    public StopManagerEvent(Manager<?, ?> manager) {
        super(manager);
    }
}
