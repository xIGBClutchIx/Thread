package dev.xigbclutch.thread.core.model;

/**
 * Snapshot of a non-empty item stack.
 *
 * @param itemId canonical item registry ID
 * @param count current stack count
 * @param maxCount maximum count allowed by the item
 * @param displayName localized convenience name, never the canonical key
 */
public record ItemStackInfo(String itemId, int count, int maxCount, String displayName) {
  public ItemStackInfo {
    itemId = ModelValidation.registryId(itemId, "itemId");
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }
    if (maxCount <= 0 || count > maxCount) {
      throw new IllegalArgumentException("maxCount must be positive and at least count");
    }
    displayName = ModelValidation.nonBlank(displayName, "displayName");
  }
}
