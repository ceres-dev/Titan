package dev.cerez.titan.core.environment.exception;

public class ManagerIsNotFoundException extends ManagerException {
    public ManagerIsNotFoundException() {
        super("El Id del gestor no existe.", -1, 404);
    }
}
