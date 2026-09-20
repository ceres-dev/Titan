package dev.cerez.titan.utils.exception;

public class MangerIsNotRunningException extends ManagerException {
  public MangerIsNotRunningException() {
    super();
  }

  public MangerIsNotRunningException(String message) {
    super(message);
  }

  public MangerIsNotRunningException(String message, Throwable cause) {
    super(message, cause);
  }

  public MangerIsNotRunningException(Throwable cause) {
    super(cause);
  }

  protected MangerIsNotRunningException(String message, Throwable cause,
                             boolean enableSuppression,
                             boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
