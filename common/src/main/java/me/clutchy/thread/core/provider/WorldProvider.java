package me.clutchy.thread.core.provider;

import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.ContainerInspectionQuery;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyContainerSnapshotResult;
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

  /**
   * Returns compact distance-ordered summaries of container block entities in loaded chunks near
   * the local player.
   */
  ToolResult<NearbyContainerResult> nearbyContainers(NearbyContainerQuery query);

  /**
   * Returns full safe snapshots from the same bounded loaded-container scan for internal read-only
   * consumers such as unified item search.
   */
  ToolResult<NearbyContainerSnapshotResult> nearbyContainerSnapshots(NearbyContainerQuery query);

  /** Returns full safe inspection data for one nearby loaded container position. */
  ToolResult<BlockInfo> inspectContainer(ContainerInspectionQuery query);
}
