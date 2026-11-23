package dev.cerez.titan.command.exception;

import lombok.Getter;

@Getter
public class UndefinedFlagException extends CommandException {

    private final String reason;
    public UndefinedFlagException(String reason){
        this.reason = reason;
    }

}

