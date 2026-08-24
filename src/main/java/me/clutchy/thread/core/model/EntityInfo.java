package me.clutchy.thread.core.model;

import java.util.Objects;

/** Bounded summary of one already-loaded entity near the local player. */
public record EntityInfo(String entityType, double distance, Position position) {
  public EntityInfo {
    entityType = ModelValidation.registryId(entityType, "entityType");
    ModelValidation.nonNegative(distance, "distance");
    Objects.requireNonNull(position, "position");
  }
}
