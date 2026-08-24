package dev.xigbclutch.thread.core.model;

import java.util.Objects;

/**
 * Snapshot of HUD-level state for the local player.
 *
 * @param health current health
 * @param maxHealth maximum health
 * @param food current food level
 * @param saturation current saturation
 * @param experienceLevel whole experience level
 * @param experienceProgress progress toward the next level in the inclusive range 0 to 1
 * @param position current world position
 * @param dimension canonical dimension registry ID
 * @param gameMode stable lower-case game mode name
 */
public record PlayerStatus(
    double health,
    double maxHealth,
    int food,
    double saturation,
    int experienceLevel,
    double experienceProgress,
    Position position,
    String dimension,
    String gameMode) {
  public PlayerStatus {
    ModelValidation.nonNegative(health, "health");
    ModelValidation.nonNegative(maxHealth, "maxHealth");
    if (health > maxHealth) {
      throw new IllegalArgumentException("health must not exceed maxHealth");
    }
    if (food < 0) {
      throw new IllegalArgumentException("food must not be negative");
    }
    ModelValidation.nonNegative(saturation, "saturation");
    if (experienceLevel < 0) {
      throw new IllegalArgumentException("experienceLevel must not be negative");
    }
    ModelValidation.finite(experienceProgress, "experienceProgress");
    if (experienceProgress < 0 || experienceProgress > 1) {
      throw new IllegalArgumentException("experienceProgress must be between 0 and 1");
    }
    Objects.requireNonNull(position, "position");
    dimension = ModelValidation.registryId(dimension, "dimension");
    gameMode = ModelValidation.nonBlank(gameMode, "gameMode");
  }
}
