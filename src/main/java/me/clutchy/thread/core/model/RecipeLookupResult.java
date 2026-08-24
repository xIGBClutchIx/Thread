package me.clutchy.thread.core.model;

import java.util.Comparator;
import java.util.List;

/** Deterministic live recipe lookup result for one canonical item ID. */
public record RecipeLookupResult(String itemId, List<RecipeInfo> recipes) {
  public RecipeLookupResult {
    itemId = ModelValidation.registryId(itemId, "itemId");
    recipes =
        ModelValidation.immutableList(recipes, "recipes").stream()
            .sorted(Comparator.comparing(RecipeInfo::recipeId).thenComparing(RecipeInfo::type))
            .toList();
    for (RecipeInfo recipe : recipes) {
      if (!recipe.result().itemId().equals(itemId)) {
        throw new IllegalArgumentException("every recipe result must match itemId");
      }
    }
  }
}
