package me.clutchy.thread.core.provider;

import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.tool.ToolResult;

/**
 * Supplies bounded loader-neutral snapshots from already-loaded world state.
 *
 * <p>Implementations must not force-load chunks and must honor the supplied query bounds.
 */
public interface WorldProvider {
  /**
   * Returns detached nearby entity identity, position, health, and reliable behavior context from
   * already-loaded state, bounded by the supplied radius and limit.
   */
  ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query);
}
