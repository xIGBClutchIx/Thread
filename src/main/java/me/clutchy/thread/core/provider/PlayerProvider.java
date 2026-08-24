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
  ToolResult<PlayerStatus> status();

  ToolResult<InventorySnapshot> inventory();

  ToolResult<EquipmentSnapshot> equipment();

  ToolResult<Optional<BlockInfo>> targetBlock();
}
