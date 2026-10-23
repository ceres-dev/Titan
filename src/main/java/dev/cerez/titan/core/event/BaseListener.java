package dev.cerez.titan.core.event;

import dev.cerez.titan.core.Manager;

public interface BaseListener {

    default void started(){}
    default void stopped(){}
    default void changeStatus(){}
}
