package me.clutchy.thread.core.model;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Snapshot of the local player's non-empty inventory slots.
 *
 * <p>Empty slots are omitted. Slots are normalized into ascending index order so serialization is
 * deterministic across providers.
 *
 * @param selectedHotbarSlot selected hotbar index from 0 through 8
 * @param slots non-empty inventory slots
 */
public record InventorySnapshot(int selectedHotbarSlot, List<InventorySlotInfo> slots) {
  /** Number of main inventory positions exposed by the V1 player inventory contract. */
  public static final int MAIN_SLOT_COUNT = 36;

  public InventorySnapshot {
    if (selectedHotbarSlot < 0 || selectedHotbarSlot > 8) {
      throw new IllegalArgumentException("selectedHotbarSlot must be between 0 and 8");
    }
    slots =
        ModelValidation.immutableList(slots, "slots").stream()
            .sorted(Comparator.comparingInt(InventorySlotInfo::slot))
            .toList();
    Set<Integer> seenSlots = new HashSet<>();
    for (InventorySlotInfo slot : slots) {
      if (!seenSlots.add(slot.slot())) {
        throw new IllegalArgumentException("inventory contains duplicate slot " + slot.slot());
      }
    }
  }
}
