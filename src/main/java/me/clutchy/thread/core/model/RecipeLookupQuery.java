package me.clutchy.thread.core.model;

/** Canonical item ID requested from the live recipe registry. */
public record RecipeLookupQuery(String itemId) {
  public RecipeLookupQuery {
    itemId = ModelValidation.registryId(itemId, "itemId");
  }
}
