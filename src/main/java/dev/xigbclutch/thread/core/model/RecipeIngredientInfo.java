package dev.xigbclutch.thread.core.model;

import java.util.List;
import java.util.TreeSet;

/**
 * One recipe ingredient group, preserving all valid item and tag alternatives.
 *
 * @param itemIds canonical item alternatives
 * @param tagIds canonical tag alternatives
 * @param count number of occurrences of this identical ingredient group
 */
public record RecipeIngredientInfo(List<String> itemIds, List<String> tagIds, int count) {
  public RecipeIngredientInfo {
    itemIds = normalizedRegistryIds(itemIds, "itemIds");
    tagIds = normalizedRegistryIds(tagIds, "tagIds");
    if (itemIds.isEmpty() && tagIds.isEmpty()) {
      throw new IllegalArgumentException("an ingredient must preserve at least one alternative");
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
