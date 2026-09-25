package dev.cerez.titan.core.exception;

public class AssetNotExitsException extends ManagerException {
    public AssetNotExitsException() {
        super("El asset no existe", -3, 404);
    }
}
