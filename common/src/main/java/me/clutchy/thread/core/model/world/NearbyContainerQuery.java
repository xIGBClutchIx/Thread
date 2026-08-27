package me.clutchy.thread.core.model.world;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Requested bounds for a nearby loaded-container query. */
public record NearbyContainerQuery(double radius, int limit) {
  /** Absolute V1 radius ceiling in blocks; configured limits may be lower. */
  public static final double HARD_MAX_RADIUS = 16.0D;

  /** Absolute V1 result ceiling; configured limits may be lower. */
  public static final int HARD_MAX_RESULTS = 64;

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
