package me.clutchy.thread.core.model.player;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import me.clutchy.thread.core.model.validation.ModelValidation;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.Position;
import me.clutchy.thread.core.model.world.StatusEffectInfo;

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
 * @param hardcore whether the current save uses hardcore rules
 * @param armor current armor value and toughness
 * @param air current and maximum air supply
 * @param activeEffects bounded active status effects ordered by registry ID
 * @param activeEffectsTruncated whether additional effects were omitted
 * @param movement current movement flags and fall distance
 * @param conditions current sleeping, fire, and freezing flags
 * @param selectedHotbarSlot selected zero-based hotbar slot
 * @param attackCooldown current attack-strength scale in the inclusive range 0 to 1
 * @param vehicle direct vehicle summary, or {@code null} when not riding
 * @param respawn player-specific respawn point, or {@code null} when Minecraft will use world spawn
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
    String gameMode,
    boolean hardcore,
    Armor armor,
    Air air,
    List<StatusEffectInfo> activeEffects,
    boolean activeEffectsTruncated,
    Movement movement,
    Conditions conditions,
    int selectedHotbarSlot,
    double attackCooldown,
    Vehicle vehicle,
    Respawn respawn) {
  /** Maximum number of active effects serialized for the player. */
  public static final int MAX_ACTIVE_EFFECTS = 64;

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
    Objects.requireNonNull(armor, "armor");
    Objects.requireNonNull(air, "air");
    activeEffects =
        ModelValidation.immutableList(activeEffects, "activeEffects").stream()
            .sorted(Comparator.comparing(StatusEffectInfo::effectId))
            .toList();
    validateEffects(activeEffects, activeEffectsTruncated);
    Objects.requireNonNull(movement, "movement");
    Objects.requireNonNull(conditions, "conditions");
    if (selectedHotbarSlot < 0 || selectedHotbarSlot >= InventorySnapshot.HOTBAR_SLOT_COUNT) {
      throw new IllegalArgumentException("selectedHotbarSlot must be between 0 and 8");
    }
    ModelValidation.finite(attackCooldown, "attackCooldown");
    if (attackCooldown < 0 || attackCooldown > 1) {
      throw new IllegalArgumentException("attackCooldown must be between 0 and 1");
    }
  }

  private static void validateEffects(
      List<StatusEffectInfo> activeEffects, boolean activeEffectsTruncated) {
    if (activeEffects.size() > MAX_ACTIVE_EFFECTS) {
      throw new IllegalArgumentException("activeEffects exceeds " + MAX_ACTIVE_EFFECTS);
    }
    Set<String> effectIds = new HashSet<>();
    for (StatusEffectInfo effect : activeEffects) {
      if (!effectIds.add(effect.effectId())) {
        throw new IllegalArgumentException(
            "activeEffects contains duplicate effect " + effect.effectId());
      }
    }
    if (activeEffectsTruncated && activeEffects.size() != MAX_ACTIVE_EFFECTS) {
      throw new IllegalArgumentException(
          "truncated activeEffects must contain the maximum serialized entries");
    }
  }

  /** Current armor protection values without exposing the raw attribute map. */
  public record Armor(int value, double toughness) {
    public Armor {
      if (value < 0) {
        throw new IllegalArgumentException("armor value must not be negative");
      }
      ModelValidation.nonNegative(toughness, "armor toughness");
    }
  }

  /** Current and maximum air supply; current may be negative while drowning. */
  public record Air(int current, int maximum) {
    public Air {
      if (maximum <= 0) {
        throw new IllegalArgumentException("maximum air must be positive");
      }
    }
  }

  /** Current movement state exposed by Minecraft's authoritative player entity. */
  public record Movement(
      boolean sprinting,
      boolean swimming,
      boolean crouching,
      boolean flying,
      boolean onGround,
      double fallDistance) {
    public Movement {
      ModelValidation.nonNegative(fallDistance, "fallDistance");
    }
  }

  /** Current bounded environmental/body conditions. */
  public record Conditions(
      boolean sleeping, boolean onFire, boolean freezing, boolean fullyFrozen) {}

  /** Compact identity of the entity the player is directly riding. */
  public record Vehicle(String entityType, String displayName, String customName) {
    public Vehicle {
      entityType = ModelValidation.registryId(entityType, "vehicle entityType");
      displayName = ModelValidation.boundedNonBlank(displayName, "vehicle displayName", 256);
      customName = ModelValidation.optionalBoundedNonBlank(customName, "vehicle customName", 256);
    }
  }

  /** Player-specific bed, anchor, or forced respawn point. */
  public record Respawn(String dimension, BlockPosition position, boolean forced) {
    public Respawn {
      dimension = ModelValidation.registryId(dimension, "respawn dimension");
      Objects.requireNonNull(position, "respawn position");
    }
  }
}
