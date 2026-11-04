package dev.cerez.titan.core.exception;

public class ConfigMalformatException extends ManagerException {

    public ConfigMalformatException() {
        super("Configurado mal formado", 400);
    }
}
