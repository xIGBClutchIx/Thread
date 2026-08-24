package dev.xigbclutch.thread.core.provider;

import dev.xigbclutch.thread.core.model.NearbyEntityQuery;
import dev.xigbclutch.thread.core.model.NearbyEntityResult;

/**
 * Supplies bounded loader-neutral snapshots from already-loaded world state.
 *
 * <p>Implementations must not force-load chunks and must honor the supplied query bounds.
 */
public interface WorldProvider {
  NearbyEntityResult nearbyEntities(NearbyEntityQuery query);
}
