package dev.cerez.titan.storage.exception;

public abstract class StorageManagerException extends RuntimeException {
    public StorageManagerException(String message) {
        super(message);
    }

    public StorageManagerException() {}
}
