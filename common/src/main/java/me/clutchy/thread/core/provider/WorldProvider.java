package me.clutchy.thread.core.provider;

import java.util.Optional;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.ContainerInspectionQuery;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyContainerSnapshotResult;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.model.world.WorldInfo;
import me.clutchy.thread.core.tool.ToolResult;

/**
 * Supplies bounded loader-neutral snapshots from already-loaded world state.
 *
 * <p>Implementations must not force-load chunks and must honor the supplied query bounds.
 */
public interface WorldProvider {
  /** Returns a detached snapshot of the player's current world and local environment. */
  ToolResult<WorldInfo> worldInfo();

  /**
   * Returns the exact normal client crosshair entity resolved from authoritative loaded server
   * state, or an empty value when no entity is targeted.
   */
  ToolResult<Optional<EntityInfo>> targetEntity();

  /**
   * Returns detached nearby entity identity, position, health, and bounded conditional vanilla
   * metadata from already-loaded state, bounded by the supplied radius and limit.
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
