package me.clutchy.thread.core.model.crafting;

import java.util.List;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Inventory assessment for one deterministic recipe variant. */
public record RecipeCraftability(
    int variant,
    String recipeId,
    String type,
    int resultCount,
    boolean craftable,
    List<IngredientAvailability> ingredients) {
  public RecipeCraftability {
    if (variant <= 0) {
      throw new IllegalArgumentException("variant must be positive");
    }
    recipeId = ModelValidation.registryId(recipeId, "recipeId");
    type = ModelValidation.registryId(type, "type");
    if (resultCount <= 0) {
      throw new IllegalArgumentException("resultCount must be positive");
    }
    ingredients = ModelValidation.immutableList(ingredients, "ingredients");
    boolean requirementsSatisfied = ingredients.stream().allMatch(value -> value.missing() == 0);
    if (craftable != requirementsSatisfied) {
      throw new IllegalArgumentException("craftable must match ingredient availability");
    }
  }
}
