package dev.cerez.titan.command.exception;

public class ArgumentTooSmallException extends CommandUserException  {

    public ArgumentTooSmallException(String reason) {
        super(reason);
    }

    public ArgumentTooSmallException() {
        super();
    }
}
