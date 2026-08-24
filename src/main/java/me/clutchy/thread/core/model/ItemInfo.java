package me.clutchy.thread.core.model;

/** Registry identity and convenience display metadata for an item. */
public record ItemInfo(String itemId, String displayName) {
  public ItemInfo {
    itemId = ModelValidation.registryId(itemId, "itemId");
    displayName = ModelValidation.nonBlank(displayName, "displayName");
  }
}
