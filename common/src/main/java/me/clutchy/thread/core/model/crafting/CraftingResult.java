package me.clutchy.thread.core.model.crafting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Deterministic craftability assessment for every recipe variant producing one item. */
public record CraftingResult(
    String itemId,
    CraftingScope scope,
    CraftingSourceStatus sourceStatus,
    boolean craftable,
    List<RecipeCraftability> recipes) {
  public CraftingResult {
    itemId = ModelValidation.registryId(itemId, "itemId");
    if (scope == null || sourceStatus == null) {
      throw new NullPointerException("scope and sourceStatus must not be null");
    }
    recipes = ModelValidation.immutableList(recipes, "recipes");
    boolean anyCraftable = recipes.stream().anyMatch(RecipeCraftability::craftable);
    if (craftable != anyCraftable) {
      throw new IllegalArgumentException("craftable must match recipe variants");
    }
    Set<Integer> variants = new HashSet<>();
    for (int index = 0; index < recipes.size(); index++) {
      RecipeCraftability recipe = recipes.get(index);
      if (!variants.add(recipe.variant()) || recipe.variant() != index + 1) {
        throw new IllegalArgumentException("recipe variants must be sequential and unique");
      }
    }
  }
}
