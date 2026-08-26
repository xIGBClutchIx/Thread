package me.clutchy.thread.core.model.world;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Requested bounds for a nearby loaded-container query. */
public record NearbyContainerQuery(double radius, int limit) {
  public NearbyContainerQuery {
    ModelValidation.finite(radius, "radius");
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be positive");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
  }
}
