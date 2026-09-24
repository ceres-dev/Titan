package dev.cerez.titan.core.environment.exception;

import lombok.Getter;

@Getter
public class ManagerException extends RuntimeException {

    private final int codeApi;
    private final int codeHtml;

    public ManagerException(String message, int codeApi, int codeHtml) {
        super(message);
        this.codeApi = codeApi;
        this.codeHtml = codeHtml;
    }
}
