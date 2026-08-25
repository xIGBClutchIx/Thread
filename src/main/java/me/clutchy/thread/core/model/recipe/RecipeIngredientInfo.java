package me.clutchy.thread.core.model.recipe;

import java.util.List;
import java.util.TreeSet;
import me.clutchy.thread.core.model.validation.ModelValidation;

/**
 * One recipe ingredient group, preserving all valid item and tag alternatives.
 *
 * @param itemIds complete resolved canonical item alternatives, including members of tags
 * @param tagIds canonical source tags retained as ingredient provenance
 * @param count number of occurrences of this identical ingredient group
 */
public record RecipeIngredientInfo(List<String> itemIds, List<String> tagIds, int count) {
  public RecipeIngredientInfo {
    itemIds = normalizedRegistryIds(itemIds, "itemIds");
    tagIds = normalizedRegistryIds(tagIds, "tagIds");
    if (itemIds.isEmpty()) {
      throw new IllegalArgumentException("itemIds must contain resolved ingredient alternatives");
    }
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }
  }

  private static List<String> normalizedRegistryIds(List<String> values, String name) {
    List<String> copy = ModelValidation.immutableList(values, name);
    TreeSet<String> normalized = new TreeSet<>();
    for (String value : copy) {
      normalized.add(ModelValidation.registryId(value, name + " entry"));
    }
    return List.copyOf(normalized);
  }
}
