package dev.cerez.titan.command.exception;

public class ReferenceNotFoundException extends CommandUserException {
    public ReferenceNotFoundException(String reason) {
        super(reason);
    }

    public ReferenceNotFoundException() {
        super();
    }
}
