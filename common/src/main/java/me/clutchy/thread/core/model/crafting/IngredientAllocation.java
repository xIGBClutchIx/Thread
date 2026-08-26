package me.clutchy.thread.core.model.crafting;

import java.util.List;
import me.clutchy.thread.core.model.item.find.FoundItemSource;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Item units assigned to one ingredient plus any live sources that supplied those units. */
public record IngredientAllocation(
    String itemId, int count, List<FoundItemSource> sourceAllocations) {
  /** Creates an allocation without live provenance, such as planned intermediate output. */
  public IngredientAllocation(String itemId, int count) {
    this(itemId, count, List.of());
  }

  public IngredientAllocation {
    itemId = ModelValidation.registryId(itemId, "itemId");
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }
    sourceAllocations = FoundItemSource.normalized(sourceAllocations, "sourceAllocations");
    int attributed =
        sourceAllocations.stream().mapToInt(FoundItemSource::count).reduce(0, Math::addExact);
    if (attributed > count) {
      throw new IllegalArgumentException("source allocation counts must not exceed count");
    }
  }
}
