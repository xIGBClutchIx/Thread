package me.clutchy.thread.core.model;

/** Requested bounds for a nearby-loaded-entity query. */
public record NearbyEntityQuery(double radius, int limit) {
  public NearbyEntityQuery {
    ModelValidation.finite(radius, "radius");
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be positive");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
  }
}
