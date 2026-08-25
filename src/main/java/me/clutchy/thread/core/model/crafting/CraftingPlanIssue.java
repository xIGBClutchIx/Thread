package me.clutchy.thread.core.model.crafting;

import java.util.List;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Structured explanation of a cycle, depth limit, or total planning-work limit. */
public record CraftingPlanIssue(
    CraftingPlanIssueType type, String itemId, int required, List<String> path) {
  public CraftingPlanIssue {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    itemId = ModelValidation.registryId(itemId, "itemId");
    if (required <= 0) {
      throw new IllegalArgumentException("required must be positive");
    }
    path =
        ModelValidation.immutableList(path, "path").stream()
            .map(entry -> ModelValidation.registryId(entry, "path entry"))
            .toList();
    if (path.isEmpty() || !path.getLast().equals(itemId)) {
      throw new IllegalArgumentException("path must end with the affected item");
    }
  }
}
