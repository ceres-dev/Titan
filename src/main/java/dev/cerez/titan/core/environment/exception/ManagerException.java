package dev.cerez.titan.core.environment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Getter
public class ManagerException extends RuntimeException {

    private final int codeApi;
    private final HttpStatus httpStatus;

    public ManagerException(String message, int codeApi, HttpStatus httpStatus) {
        super(message);
        this.codeApi = codeApi;
        this.httpStatus = httpStatus;
    }

    public ResponseEntity<ResponseException> getResponse() {
        return ResponseEntity.status(httpStatus).body(new ResponseException(codeApi, getMessage()));
    }

    public record ResponseException(int code, String message) {}
}
