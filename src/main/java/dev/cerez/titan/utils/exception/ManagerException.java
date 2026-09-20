package dev.cerez.titan.utils.exception;

public class ManagerException extends RuntimeException {
    public ManagerException() {
        super();
    }

    public ManagerException(String message) {
        super(message);
    }

    public ManagerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ManagerException(Throwable cause) {
        super(cause);
    }

    protected ManagerException(String message, Throwable cause,
                               boolean enableSuppression,
                               boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
