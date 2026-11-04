package dev.cerez.titan.core.exception;

public class AlreadySubscribedException extends ManagerException {
    public AlreadySubscribedException() {
        super("Ya estas subscrito", 400);
    }
}
