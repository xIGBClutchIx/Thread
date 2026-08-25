package me.clutchy.thread.core.model;

import java.util.Objects;

/**
 * Snapshot of one equipment position.
 *
 * @param slot stable equipment position
 * @param item enriched item snapshot, or {@code null} when the position is empty
 */
public record EquipmentSlotInfo(EquipmentPosition slot, ItemStackInfo item) {
  public EquipmentSlotInfo {
    Objects.requireNonNull(slot, "slot");
  }
}
