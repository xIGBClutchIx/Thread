package me.clutchy.thread.core.model.crafting;

import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.model.item.find.FoundItemSource;
import me.clutchy.thread.core.model.item.find.FoundItemSourceType;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Deterministic, bounded recipe plan for one requested item. */
public record CraftingPlan(
    String itemId,
    CraftingScope scope,
    CraftingSourceStatus sourceStatus,
    int requested,
    int satisfiedFromInventory,
    List<FoundItemSource> satisfiedFromSources,
    boolean craftable,
    int maxDepth,
    List<CraftingPlanStep> steps,
    List<MissingMaterial> missingMaterials,
    List<CraftingPlanIssue> issues) {
  public CraftingPlan {
    itemId = ModelValidation.registryId(itemId, "itemId");
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(sourceStatus, "sourceStatus");
    if (requested <= 0) {
      throw new IllegalArgumentException("requested must be positive");
    }
    if (satisfiedFromInventory < 0 || satisfiedFromInventory > requested) {
      throw new IllegalArgumentException(
          "satisfiedFromInventory must be between zero and requested");
    }
    satisfiedFromSources = FoundItemSource.normalized(satisfiedFromSources, "satisfiedFromSources");
    int attributed =
        satisfiedFromSources.stream().mapToInt(FoundItemSource::count).reduce(0, Math::addExact);
    if (attributed > requested) {
      throw new IllegalArgumentException("satisfied source counts must not exceed requested");
    }
    int inventoryAttributed =
        satisfiedFromSources.stream()
            .filter(source -> source.sourceType() == FoundItemSourceType.PLAYER_INVENTORY)
            .mapToInt(FoundItemSource::count)
            .reduce(0, Math::addExact);
    if (satisfiedFromInventory != inventoryAttributed) {
      throw new IllegalArgumentException(
          "satisfiedFromInventory must match player inventory source allocations");
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
