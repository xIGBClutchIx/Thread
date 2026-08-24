package me.clutchy.thread.platform.fabric.threading;

import java.time.Duration;
import java.util.Objects;

/** Indicates that a queued game-thread read did not complete inside its configured deadline. */
public final class GameThreadTimeoutException extends RuntimeException {
  private final Duration timeout;

  public GameThreadTimeoutException(Duration timeout) {
    super("Timed out waiting for the game thread after " + timeout.toMillis() + " ms");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
  }

  /** Returns the deadline that elapsed. */
  public Duration timeout() {
    return timeout;
  }
}
