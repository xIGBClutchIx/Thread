package me.clutchy.thread.core.model.world;

import java.util.Objects;
import me.clutchy.thread.core.model.validation.ModelValidation;

/**
 * Bounded summary of one already-loaded entity near the local player.
 *
 * <p>Classification is nullable because Thread only labels entity families that Minecraft exposes
 * through reliable behavior markers. Health values are present exactly when {@code living} is true.
 */
public record EntityInfo(
    String entityType,
    String displayName,
    String customName,
    double distance,
    Position position,
    boolean living,
    Double health,
    Double maxHealth,
    EntityClassification classification) {
  public EntityInfo {
    entityType = ModelValidation.registryId(entityType, "entityType");
    displayName = ModelValidation.boundedNonBlank(displayName, "displayName", 256);
    customName = ModelValidation.optionalBoundedNonBlank(customName, "customName", 256);
    ModelValidation.nonNegative(distance, "distance");
    Objects.requireNonNull(position, "position");
    if (living) {
      if (health == null || maxHealth == null) {
        throw new IllegalArgumentException("living entities must include health and maxHealth");
      }
      ModelValidation.nonNegative(health, "health");
      if (!Double.isFinite(maxHealth) || maxHealth <= 0) {
        throw new IllegalArgumentException("maxHealth must be finite and positive");
      }
    } else if (health != null || maxHealth != null || classification != null) {
      throw new IllegalArgumentException(
          "non-living entities cannot include health or behavior classification");
    }
  }
}
