package me.clutchy.thread.core.model.player;

import java.util.Objects;
import me.clutchy.thread.core.model.item.ItemStackInfo;

/** Snapshot of one non-empty player inventory slot. */
public record InventorySlotInfo(int slot, ItemStackInfo stack) {
  public InventorySlotInfo {
    if (slot < 0 || slot >= InventorySnapshot.MAIN_SLOT_COUNT) {
      throw new IllegalArgumentException(
          "slot must be between 0 and " + (InventorySnapshot.MAIN_SLOT_COUNT - 1));
    }
    Objects.requireNonNull(stack, "stack");
  }
}
