package dev.cerez.titan.command.exception;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
public class CommandUserException extends CommandException {

    @Nullable
    private final String reason;

    public CommandUserException(@Nullable String reason) {
        this.reason = reason;
    }

    public CommandUserException() {
      this.reason = null;
    }
}
