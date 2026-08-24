package me.clutchy.thread.platform.fabric.threading;

/** Indicates that a logical game-thread read could not be dispatched or completed. */
public final class GameThreadExecutionException extends RuntimeException {
  public GameThreadExecutionException(String message) {
    super(message);
  }

  public GameThreadExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
