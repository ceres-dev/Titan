package dev.cerez.titan.command.exception;

public class IllegalCommandArgumentException extends CommandUserException {
    public IllegalCommandArgumentException(String reason) {
      super(reason);
    }

    public IllegalCommandArgumentException() {
      super();
    }
}
