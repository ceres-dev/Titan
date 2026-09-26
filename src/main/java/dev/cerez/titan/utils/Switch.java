package dev.cerez.titan.utils;

import dev.cerez.titan.core.exception.ManagerIsNotRunningException;

public interface Switch {

    void start();

    void stop();

    boolean isRunning();

    default void runningOrException(){
        if (!this.isRunning()){
            throw new ManagerIsNotRunningException();
        }
    }
}
