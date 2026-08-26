package me.clutchy.thread.core.model.recipe;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Canonical item ID requested from the live recipe registry. */
public record RecipeLookupQuery(String itemId) {
  public RecipeLookupQuery {
    itemId = ModelValidation.registryId(itemId, "itemId");
  }
}
