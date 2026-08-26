package me.clutchy.thread.core.model.crafting;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Completeness and applied bounds for the detached item-source snapshot used by crafting. */
public record CraftingSourceStatus(
    boolean complete,
    Double nearbyRadius,
    Integer nearbyContainerLimit,
    boolean nearbyContainersTruncated,
    int unresolvedContainersSkipped,
    int contentLimitedContainersSkipped) {
  public CraftingSourceStatus {
    if ((nearbyRadius == null) != (nearbyContainerLimit == null)) {
      throw new IllegalArgumentException(
          "nearby radius and limit must either both be set or absent");
    }
    if (nearbyRadius != null) {
      ModelValidation.finite(nearbyRadius, "nearbyRadius");
      if (nearbyRadius <= 0) {
        throw new IllegalArgumentException("nearbyRadius must be positive");
      }
      if (nearbyContainerLimit <= 0) {
        throw new IllegalArgumentException("nearbyContainerLimit must be positive");
      }
    }
    if (unresolvedContainersSkipped < 0 || contentLimitedContainersSkipped < 0) {
      throw new IllegalArgumentException("skipped container counts must not be negative");
    }
    boolean knownIncomplete =
        nearbyContainersTruncated
            || unresolvedContainersSkipped > 0
            || contentLimitedContainersSkipped > 0;
    if (complete && knownIncomplete) {
      throw new IllegalArgumentException("complete source discovery cannot report omissions");
    }
  }
}
