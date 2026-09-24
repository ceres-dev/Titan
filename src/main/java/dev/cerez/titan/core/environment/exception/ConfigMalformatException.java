package dev.cerez.titan.core.environment.exception;

public class ConfigMalformatException extends ManagerException {

    public ConfigMalformatException() {
        super("Configurado mal formado", -2, 400);
    }
}
