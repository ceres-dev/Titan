package dev.cerez.titan.core.environment.exception;

import org.springframework.http.HttpStatus;

public class AssetNotExitsException extends ManagerException {
    public AssetNotExitsException() {
        super("El asset no existe", -3, HttpStatus.NOT_FOUND);
    }
}
