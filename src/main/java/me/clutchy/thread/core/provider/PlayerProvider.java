package me.clutchy.thread.core.provider;

import java.util.Optional;
import me.clutchy.thread.core.model.BlockInfo;
import me.clutchy.thread.core.model.EquipmentSnapshot;
import me.clutchy.thread.core.model.InventorySnapshot;
import me.clutchy.thread.core.model.PlayerStatus;
import me.clutchy.thread.core.tool.ToolResult;

/**
 * Supplies loader-neutral snapshots of the local player and camera target.
 *
 * <p>Implementations must return detached Thread DTOs and own any client/logical-server thread
 * dispatch. An empty target means the camera is not currently targeting a valid block.
 */
public interface PlayerProvider {
  /** Returns the current local-player status after supported-session validation. */
  ToolResult<PlayerStatus> status();

  /**
   * Returns the 36 main-inventory positions as non-empty slots plus the selected hotbar slot.
   *
   * <p>Callers use {@link #equipment()} for held and armor positions.
   */
  ToolResult<InventorySnapshot> inventory();

  /** Returns all six held/armor positions, with a null item in each empty position. */
  ToolResult<EquipmentSnapshot> equipment();

  /**
   * Returns the normal client raycast target with authoritative loaded server state, or an empty
   * value when no block is targeted.
   */
  ToolResult<Optional<BlockInfo>> targetBlock();
}
