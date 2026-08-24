package me.clutchy.thread.core.model;

import java.util.Comparator;
import java.util.List;

/** Result of a bounded query over entities that were already loaded. */
public record NearbyEntityResult(
    double radius, int limit, boolean truncated, List<EntityInfo> entities) {
  public NearbyEntityResult {
    ModelValidation.finite(radius, "radius");
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be positive");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    entities =
        ModelValidation.immutableList(entities, "entities").stream()
            .sorted(
                Comparator.comparingDouble(EntityInfo::distance)
                    .thenComparing(EntityInfo::entityType)
                    .thenComparingDouble(entity -> entity.position().x())
                    .thenComparingDouble(entity -> entity.position().y())
                    .thenComparingDouble(entity -> entity.position().z()))
            .toList();
    if (entities.size() > limit) {
      throw new IllegalArgumentException("entity count must not exceed limit");
    }
  }
}
