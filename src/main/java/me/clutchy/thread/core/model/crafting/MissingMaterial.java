package me.clutchy.thread.core.model.crafting;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Final raw or otherwise unresolvable material that must be acquired outside the plan. */
public record MissingMaterial(String itemId, int count) {
  public MissingMaterial {
    itemId = ModelValidation.registryId(itemId, "itemId");
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }
  }
}
