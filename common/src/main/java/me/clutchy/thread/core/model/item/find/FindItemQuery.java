package me.clutchy.thread.core.model.item.find;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Bounded query for matching live items across player and nearby-container sources. */
public record FindItemQuery(String query, double radius, int containerLimit, int itemLimit) {
  public FindItemQuery {
    query = ModelValidation.boundedNonBlank(query, "query", 128).strip();
    ModelValidation.finite(radius, "radius");
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be positive");
    }
    if (containerLimit <= 0) {
      throw new IllegalArgumentException("containerLimit must be positive");
    }
    if (itemLimit <= 0) {
      throw new IllegalArgumentException("itemLimit must be positive");
    }
  }
}
