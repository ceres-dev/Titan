package dev.cerez.titan.core.exception;

import lombok.Getter;

@Getter
public class FrontendException extends RuntimeException {

    private final int codeApi;
    private final int status;

    public FrontendException(String message, int codeApi, int status) {
        super(message);
        this.codeApi = codeApi;
        this.status = status;
    }

    public ManagerException.ResponseException getResponse() {
        return new ManagerException.ResponseException(codeApi, getMessage());
    }

    public record ResponseException(int code, String message) {}
}
