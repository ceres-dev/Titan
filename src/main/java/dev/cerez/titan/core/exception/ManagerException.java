package dev.cerez.titan.core.exception;

import lombok.Getter;

@Getter
public abstract class ManagerException extends FrontendException {

    public ManagerException(String message, int status) {
        super(message, -1, status);
    }

}
