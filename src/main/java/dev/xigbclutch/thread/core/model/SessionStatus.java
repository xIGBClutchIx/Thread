package dev.xigbclutch.thread.core.model;

import java.util.Objects;

/**
 * Snapshot of whether the running client is in a supported gameplay session.
 *
 * @param state stable high-level client state
 * @param worldLoaded whether a client world is present
 * @param playerAvailable whether the local player is present
 * @param supported whether V1 gameplay tools may expose state
 * @param reason why gameplay tools are unavailable, or {@code null} when supported
 */
public record SessionStatus(
    SessionState state,
    boolean worldLoaded,
    boolean playerAvailable,
    boolean supported,
    SessionStatusReason reason) {
  public SessionStatus {
    Objects.requireNonNull(state, "state");
    if (supported && state != SessionState.SINGLEPLAYER) {
      throw new IllegalArgumentException("only a single-player session can be supported");
    }
    if (supported && (!worldLoaded || !playerAvailable || reason != null)) {
      throw new IllegalArgumentException(
          "a supported session requires a world, player, and no reason");
    }
    if (!supported && reason == null) {
      throw new IllegalArgumentException("an unsupported session requires a reason");
    }
  }
}
