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

  /** Returns a detached snapshot of non-empty inventory slots and the selected hotbar slot. */
  ToolResult<InventorySnapshot> inventory();

  /** Returns a detached snapshot of held and equipped items. */
  ToolResult<EquipmentSnapshot> equipment();

  /** Returns the normal client raycast target, or an empty value when no block is targeted. */
  ToolResult<Optional<BlockInfo>> targetBlock();
}
