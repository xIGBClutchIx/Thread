package me.clutchy.thread.core.model.crafting;

import java.util.List;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Deterministic, bounded recipe plan for one requested item. */
public record CraftingPlan(
    String itemId,
    int requested,
    int satisfiedFromInventory,
    boolean craftable,
    int maxDepth,
    List<CraftingPlanStep> steps,
    List<MissingMaterial> missingMaterials,
    List<CraftingPlanIssue> issues) {
  public CraftingPlan {
    itemId = ModelValidation.registryId(itemId, "itemId");
    if (requested <= 0) {
      throw new IllegalArgumentException("requested must be positive");
    }
    if (satisfiedFromInventory < 0 || satisfiedFromInventory > requested) {
      throw new IllegalArgumentException(
          "satisfiedFromInventory must be between zero and requested");
    }
    if (maxDepth <= 0) {
      throw new IllegalArgumentException("maxDepth must be positive");
    }
    steps = ModelValidation.immutableList(steps, "steps");
    for (int index = 0; index < steps.size(); index++) {
      if (steps.get(index).step() != index + 1) {
        throw new IllegalArgumentException("plan steps must be sequential");
      }
    }
    missingMaterials = ModelValidation.immutableList(missingMaterials, "missingMaterials");
    issues = ModelValidation.immutableList(issues, "issues");
    boolean fullyResolved = missingMaterials.isEmpty() && issues.isEmpty();
    if (craftable != fullyResolved) {
      throw new IllegalArgumentException(
          "craftable must match missing materials and safety issues");
    }
  }
}
