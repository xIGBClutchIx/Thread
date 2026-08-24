package dev.xigbclutch.thread.core.provider;

import dev.xigbclutch.thread.core.model.BlockInfo;
import dev.xigbclutch.thread.core.model.EquipmentSnapshot;
import dev.xigbclutch.thread.core.model.InventorySnapshot;
import dev.xigbclutch.thread.core.model.PlayerStatus;
import java.util.Optional;

/**
 * Supplies loader-neutral snapshots of the local player and camera target.
 *
 * <p>Implementations must return detached Thread DTOs and own any client/logical-server thread
 * dispatch. An empty target means the camera is not currently targeting a valid block.
 */
public interface PlayerProvider {
  PlayerStatus status();

  InventorySnapshot inventory();

  EquipmentSnapshot equipment();

  Optional<BlockInfo> targetBlock();
}
