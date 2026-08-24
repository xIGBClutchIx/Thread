package me.clutchy.thread.core.model;

import java.util.List;
import java.util.Objects;

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
