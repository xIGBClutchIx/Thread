package me.clutchy.thread.core.model.recipe;

import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Snapshot of one live-game recipe and its uncollapsed ingredient alternatives. */
public record RecipeInfo(
    String recipeId, String type, ItemStackInfo result, List<RecipeIngredientInfo> ingredients) {
  public RecipeInfo {
    recipeId = ModelValidation.registryId(recipeId, "recipeId");
    type = ModelValidation.registryId(type, "type");
    Objects.requireNonNull(result, "result");
    ingredients = ModelValidation.immutableList(ingredients, "ingredients");
  }
}
