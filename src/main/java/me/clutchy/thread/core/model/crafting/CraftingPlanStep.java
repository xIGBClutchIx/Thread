package me.clutchy.thread.core.model.crafting;

import java.util.List;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** One post-order crafting operation in a deterministic recursive plan. */
public record CraftingPlanStep(
    int step,
    String itemId,
    int variant,
    String recipeId,
    String type,
    int executions,
    int resultCount,
    List<IngredientAvailability> ingredients) {
  public CraftingPlanStep {
    if (step <= 0 || variant <= 0 || executions <= 0 || resultCount <= 0) {
      throw new IllegalArgumentException(
          "step, variant, executions, and resultCount must be positive");
    }
    itemId = ModelValidation.registryId(itemId, "itemId");
    recipeId = ModelValidation.registryId(recipeId, "recipeId");
    type = ModelValidation.registryId(type, "type");
    ingredients = ModelValidation.immutableList(ingredients, "ingredients");
  }
}
