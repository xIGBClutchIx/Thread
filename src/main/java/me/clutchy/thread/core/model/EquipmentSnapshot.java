package me.clutchy.thread.core.model;

/**
 * Snapshot of held and equipped player items.
 *
 * <p>A {@code null} component means that equipment position is empty.
 */
public record EquipmentSnapshot(
    ItemStackInfo mainHand,
    ItemStackInfo offHand,
    ItemStackInfo head,
    ItemStackInfo chest,
    ItemStackInfo legs,
    ItemStackInfo feet) {}
