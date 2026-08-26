package me.clutchy.thread.core.model.crafting;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Inventory units assigned to one recipe ingredient from a canonical item alternative. */
public record IngredientAllocation(String itemId, int count) {
  public IngredientAllocation {
    itemId = ModelValidation.registryId(itemId, "itemId");
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }
  }
}
