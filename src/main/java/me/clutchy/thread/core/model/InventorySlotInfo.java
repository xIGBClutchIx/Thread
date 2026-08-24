package me.clutchy.thread.core.model;

import java.util.Objects;

/** Snapshot of one non-empty player inventory slot. */
public record InventorySlotInfo(int slot, ItemStackInfo stack) {
  public InventorySlotInfo {
    if (slot < 0) {
      throw new IllegalArgumentException("slot must not be negative");
    }
    Objects.requireNonNull(stack, "stack");
  }
}
