package dev.cerez.titan.core.environment.exception;

import org.springframework.http.HttpStatus;

public class ManagerIsNotFoundException extends ManagerException {
    public ManagerIsNotFoundException() {
        super("El Id del gestor no existe.", -1, HttpStatus.NOT_FOUND);
    }
}
