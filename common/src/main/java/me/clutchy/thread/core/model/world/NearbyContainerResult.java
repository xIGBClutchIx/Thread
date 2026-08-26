package me.clutchy.thread.core.model.world;

import java.util.Comparator;
import java.util.List;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Result of a bounded query over container block entities in already-loaded chunks. */
public record NearbyContainerResult(
    double radius, int limit, boolean truncated, List<NearbyContainerSummary> containers) {
  public NearbyContainerResult {
    ModelValidation.finite(radius, "radius");
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be positive");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    containers =
        ModelValidation.immutableList(containers, "containers").stream()
            .sorted(
                Comparator.comparingDouble(NearbyContainerSummary::distance)
                    .thenComparing(NearbyContainerSummary::blockId)
                    .thenComparingInt(container -> container.position().x())
                    .thenComparingInt(container -> container.position().y())
                    .thenComparingInt(container -> container.position().z()))
            .toList();
    if (containers.size() > limit) {
      throw new IllegalArgumentException("container count must not exceed limit");
    }
  }
}
