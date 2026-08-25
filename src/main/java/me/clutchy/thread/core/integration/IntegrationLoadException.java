package me.clutchy.thread.core.integration;

/** Controlled failure to resolve or construct an optional integration implementation. */
public final class IntegrationLoadException extends Exception {
  public IntegrationLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
