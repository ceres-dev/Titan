package dev.cerez.titan.core.exception;

public class ManagerIsNotRunningException extends ManagerException {
    public ManagerIsNotRunningException() {
        super("El gestor no esta corriendo", -4, 406);
    }
}
