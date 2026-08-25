package me.clutchy.thread.core.model.player;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import me.clutchy.thread.core.model.validation.ModelValidation;

/**
 * Complete snapshot of held and equipped player items.
 *
 * <p>Each snapshot contains all six positions. An empty position has a {@code null} item, giving
 * clients one absence convention.
 */
public record EquipmentSnapshot(List<EquipmentSlotInfo> slots) {
  public EquipmentSnapshot {
    slots =
        ModelValidation.immutableList(slots, "slots").stream()
            .sorted(Comparator.comparing(EquipmentSlotInfo::slot))
            .toList();
    Set<EquipmentPosition> positions = EnumSet.noneOf(EquipmentPosition.class);
    for (EquipmentSlotInfo slot : slots) {
      if (!positions.add(slot.slot())) {
        throw new IllegalArgumentException("equipment contains duplicate slot " + slot.slot());
      }
    }
    if (!positions.equals(EnumSet.allOf(EquipmentPosition.class))) {
      throw new IllegalArgumentException(
          "equipment must contain every supported slot exactly once");
    }
  }
}
