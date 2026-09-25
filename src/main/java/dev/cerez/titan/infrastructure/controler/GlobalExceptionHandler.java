package dev.cerez.titan.infrastructure.controler;

import dev.cerez.titan.core.exception.FrontendException;
import dev.cerez.titan.core.exception.ManagerException;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ManagerException.class)
    public ResponseEntity<FrontendException.ResponseException> handleFrontendException(@NotNull FrontendException exception) {
        return ResponseEntity.status(exception.getStatus()).body(exception.getResponse());
    }
}
