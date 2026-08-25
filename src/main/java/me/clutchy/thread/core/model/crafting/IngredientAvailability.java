package me.clutchy.thread.core.model.crafting;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Deterministic inventory allocation and shortage for one recipe ingredient group. */
public record IngredientAvailability(
    List<String> itemIds,
    List<String> tagIds,
    int required,
    int available,
    int missing,
    List<IngredientAllocation> allocations) {
  public IngredientAvailability {
    itemIds = normalizedRegistryIds(itemIds, "itemIds");
    tagIds = normalizedRegistryIds(tagIds, "tagIds");
    if (itemIds.isEmpty()) {
      throw new IllegalArgumentException("itemIds must contain resolved ingredient alternatives");
    }
    if (required <= 0) {
      throw new IllegalArgumentException("required must be positive");
    }
    if (available < 0 || available > required) {
      throw new IllegalArgumentException("available must be between zero and required");
    }
    if (missing != required - available) {
      throw new IllegalArgumentException("missing must equal required minus available");
    }
    allocations =
        ModelValidation.immutableList(allocations, "allocations").stream()
            .sorted(Comparator.comparing(IngredientAllocation::itemId))
            .toList();
    Set<String> allocatedItems = new HashSet<>();
    int allocatedCount = 0;
    for (IngredientAllocation allocation : allocations) {
      if (!itemIds.contains(allocation.itemId())) {
        throw new IllegalArgumentException("allocation must use a declared item alternative");
      }
      if (!allocatedItems.add(allocation.itemId())) {
        throw new IllegalArgumentException("allocations contain a duplicate item ID");
      }
      allocatedCount = Math.addExact(allocatedCount, allocation.count());
    }
    if (allocatedCount != available) {
      throw new IllegalArgumentException("allocation counts must sum to available");
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
