package dev.cerez.titan.core.environment.exception;

import org.springframework.http.HttpStatus;

public class ConfigMalformatException extends ManagerException {

    public ConfigMalformatException() {
        super("Configurado mal formado", -2, HttpStatus.BAD_REQUEST);
    }
}
