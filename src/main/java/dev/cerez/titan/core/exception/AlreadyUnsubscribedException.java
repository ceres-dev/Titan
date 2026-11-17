package dev.cerez.titan.core.exception;

public class AlreadyUnsubscribedException extends ManagerException {
    public AlreadyUnsubscribedException() {
        super("Ya estas desubscrito", 400);
    }
}
