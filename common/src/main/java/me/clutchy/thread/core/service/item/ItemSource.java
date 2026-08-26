package me.clutchy.thread.core.service.item;

import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.item.find.FindItemQuery;
import me.clutchy.thread.core.model.item.find.FoundItemSourceType;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.validation.ModelValidation;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.tool.ToolResult;

/** Supplies one bounded transport-independent set of located live item stacks. */
@FunctionalInterface
public interface ItemSource {
  /** Reads one bounded detached snapshot for the requested search scope. */
  ToolResult<Snapshot> read(FindItemQuery query);

  /** One source read plus whether its nearby-container enumeration was truncated. */
  record Snapshot(boolean containersTruncated, List<Entry> entries) {
    public Snapshot {
      entries = ModelValidation.immutableList(entries, "entries");
    }
  }

  /** One non-empty item stack and its exact player or container location. */
  record Entry(
      ItemStackInfo item,
      FoundItemSourceType sourceType,
      Integer inventorySlot,
      EquipmentPosition equipmentSlot,
      String containerSlot,
      BlockPosition containerPosition,
      String containerTypeId,
      Double distance) {
    public Entry {
      Objects.requireNonNull(item, "item");
      Objects.requireNonNull(sourceType, "sourceType");
      containerSlot =
          containerSlot == null
              ? null
              : ModelValidation.boundedNonBlank(containerSlot, "containerSlot", 64);
      containerTypeId = ModelValidation.optionalRegistryId(containerTypeId, "containerTypeId");
      if (distance != null) {
        ModelValidation.nonNegative(distance, "distance");
      }
      switch (sourceType) {
        case PLAYER_INVENTORY -> {
          if (inventorySlot == null
              || inventorySlot < 0
              || inventorySlot >= InventorySnapshot.MAIN_SLOT_COUNT) {
            throw new IllegalArgumentException("player inventory entry requires a valid slot");
          }
          requireNull(equipmentSlot, containerSlot, containerPosition, containerTypeId, distance);
        }
        case PLAYER_EQUIPMENT -> {
          Objects.requireNonNull(equipmentSlot, "equipmentSlot");
          requireNull(inventorySlot, containerSlot, containerPosition, containerTypeId, distance);
        }
        case NEARBY_CONTAINER -> {
          Objects.requireNonNull(containerSlot, "containerSlot");
          Objects.requireNonNull(containerPosition, "containerPosition");
          Objects.requireNonNull(containerTypeId, "containerTypeId");
          Objects.requireNonNull(distance, "distance");
          requireNull(inventorySlot, equipmentSlot);
        }
        default -> throw new IllegalArgumentException("unsupported item source type " + sourceType);
      }
    }

    private static void requireNull(Object... values) {
      for (Object value : values) {
        if (value != null) {
          throw new IllegalArgumentException(
              "item source entry contains incompatible location data");
        }
      }
    }
  }
}
