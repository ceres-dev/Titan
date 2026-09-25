package dev.cerez.titan.core.exception;

import lombok.Getter;

@Getter
public class ManagerException extends FrontendException {

    public ManagerException(String message, int codeApi, int status) {
        super(message, codeApi, status);
    }

}
