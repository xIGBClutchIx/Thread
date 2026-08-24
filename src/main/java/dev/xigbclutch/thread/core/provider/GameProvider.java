package dev.xigbclutch.thread.core.provider;

import dev.xigbclutch.thread.core.model.GameInfo;
import dev.xigbclutch.thread.core.model.SessionStatus;

/**
 * Supplies loader-neutral snapshots of application/session state and runtime versions.
 *
 * <p>Implementations own any logical-thread dispatch required before returning these values.
 */
public interface GameProvider {
  SessionStatus sessionStatus();

  GameInfo gameInfo();
}
