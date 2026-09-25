package dev.cerez.titan.core.exception;

public class MangerIsNotRunningException extends ManagerException {
    public MangerIsNotRunningException() {
        super("El gestor ya esta corriendo", -4, 406);
    }
}
