package me.clutchy.thread.core.model.item;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Deterministic bounded item-registry search result. */
public record ItemSearchResult(String query, int limit, boolean truncated, List<ItemInfo> items) {
  public ItemSearchResult {
    query = ModelValidation.nonBlank(query, "query").strip();
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    items =
        ModelValidation.immutableList(items, "items").stream()
            .sorted(Comparator.comparing(ItemInfo::itemId))
            .toList();
    if (items.size() > limit) {
      throw new IllegalArgumentException("item count must not exceed limit");
    }
    if (truncated && items.size() != limit) {
      throw new IllegalArgumentException("a truncated result must fill its requested limit");
    }
    Set<String> seenItemIds = new HashSet<>();
    for (ItemInfo item : items) {
      if (!seenItemIds.add(item.itemId())) {
        throw new IllegalArgumentException(
            "item search contains duplicate itemId " + item.itemId());
      }
    }
  }
}
