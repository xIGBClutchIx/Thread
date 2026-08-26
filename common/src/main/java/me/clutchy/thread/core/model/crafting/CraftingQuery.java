package me.clutchy.thread.core.model.crafting;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Canonical crafting target plus an optional live item-source scope. */
public record CraftingQuery(String itemId, CraftingScope scope) {
  public CraftingQuery {
    itemId = ModelValidation.registryId(itemId, "itemId");
    scope = scope == null ? CraftingScope.PLAYER_ONLY : scope;
  }
}
