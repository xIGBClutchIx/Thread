package me.clutchy.thread.core.provider;

import me.clutchy.thread.core.model.GameInfo;
import me.clutchy.thread.core.model.SessionStatus;

/**
 * Supplies loader-neutral snapshots of application/session state and runtime versions.
 *
 * <p>Implementations own any logical-thread dispatch required before returning these values.
 */
public interface GameProvider {
  /** Returns a detached snapshot suitable for preflight checks in every client state. */
  SessionStatus sessionStatus();

  /** Returns immutable runtime version metadata and does not require a loaded world. */
  GameInfo gameInfo();
}
