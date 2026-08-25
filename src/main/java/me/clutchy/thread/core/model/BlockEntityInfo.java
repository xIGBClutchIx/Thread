package me.clutchy.thread.core.model;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Safe selected state from a targeted block entity.
 *
 * <p>Items and state are supplied by registered platform inspectors; this contract never contains
 * raw NBT or Minecraft component objects. Empty inventory slots are omitted.
 */
public record BlockEntityInfo(
    String typeId, int inventorySize, List<BlockEntityItemInfo> items, Map<String, String> state) {
  /** Maximum number of block-entity item positions retained in one response. */
  public static final int MAX_ITEMS = 64;

  public BlockEntityInfo {
    typeId = ModelValidation.registryId(typeId, "typeId");
    if (inventorySize < 0) {
      throw new IllegalArgumentException("inventorySize must not be negative");
    }
    items =
        ModelValidation.immutableList(items, "items").stream()
            .sorted(Comparator.comparing(BlockEntityItemInfo::slot))
            .toList();
    if (items.size() > inventorySize || items.size() > MAX_ITEMS) {
      throw new IllegalArgumentException(
          "non-empty item count must not exceed inventorySize or " + MAX_ITEMS);
    }
    Set<String> slots = new HashSet<>();
    for (BlockEntityItemInfo item : items) {
      if (!slots.add(item.slot())) {
        throw new IllegalArgumentException("block entity contains duplicate slot " + item.slot());
      }
    }
    state = ModelValidation.immutableSortedMap(state, "state");
  }
}
