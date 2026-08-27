package me.clutchy.thread.core.model.world;

import java.util.Objects;
import java.util.Set;
import me.clutchy.thread.core.model.validation.ModelValidation;

/**
 * Detached snapshot of useful world and local-environment context around the player.
 *
 * @param dimensionId canonical ID of the player's current dimension
 * @param biomeId canonical ID of the biome at the player's block position
 * @param biomeName localized biome name when the active language contains one
 * @param playerPosition authoritative integrated-server player position in blocks
 * @param worldSpawnDimensionId canonical dimension ID containing the global world spawn
 * @param worldSpawnPosition integer global world-spawn position
 * @param distanceFromSpawn straight-line block distance to spawn, or {@code null} across dimensions
 * @param difficulty stable lower-case world difficulty
 * @param hardcore whether the current save is configured as hardcore
 * @param gameTimeTicks total current-level age in game ticks
 * @param dayTimeTicks total overworld clock ticks used by time commands and the daylight cycle
 * @param worldDay zero-based day derived from {@code dayTimeTicks}
 * @param timeOfDayTicks tick within the current 24,000-tick day
 * @param daylightState local dimension daylight state
 * @param raining whether the current level's global rain state is active
 * @param thundering whether the current level's global thunder state is active
 * @param localLightLevel combined local raw light at the player's block position, from 0 to 15
 * @param moonPhase native lower-case moon phase at the player's position
 * @param biomeTemperature biome base temperature exposed by Minecraft
 * @param biomeHasPrecipitation whether the biome supports precipitation
 */
public record WorldInfo(
    String dimensionId,
    String biomeId,
    String biomeName,
    Position playerPosition,
    String worldSpawnDimensionId,
    BlockPosition worldSpawnPosition,
    Double distanceFromSpawn,
    String difficulty,
    boolean hardcore,
    long gameTimeTicks,
    long dayTimeTicks,
    long worldDay,
    int timeOfDayTicks,
    DaylightState daylightState,
    boolean raining,
    boolean thundering,
    int localLightLevel,
    String moonPhase,
    double biomeTemperature,
    boolean biomeHasPrecipitation) {
  /** Number of game ticks in one Minecraft day. */
  public static final int TICKS_PER_DAY = 24_000;

  private static final Set<String> DIFFICULTIES = Set.of("peaceful", "easy", "normal", "hard");
  private static final Set<String> MOON_PHASES =
      Set.of(
          "full_moon",
          "waning_gibbous",
          "third_quarter",
          "waning_crescent",
          "new_moon",
          "waxing_crescent",
          "first_quarter",
          "waxing_gibbous");

  public WorldInfo {
    dimensionId = ModelValidation.registryId(dimensionId, "dimensionId");
    biomeId = ModelValidation.registryId(biomeId, "biomeId");
    biomeName = ModelValidation.optionalBoundedNonBlank(biomeName, "biomeName", 256);
    Objects.requireNonNull(playerPosition, "playerPosition");
    worldSpawnDimensionId =
        ModelValidation.registryId(worldSpawnDimensionId, "worldSpawnDimensionId");
    Objects.requireNonNull(worldSpawnPosition, "worldSpawnPosition");
    if (distanceFromSpawn != null) {
      ModelValidation.nonNegative(distanceFromSpawn, "distanceFromSpawn");
    }
    if (dimensionId.equals(worldSpawnDimensionId) != (distanceFromSpawn != null)) {
      throw new IllegalArgumentException(
          "distanceFromSpawn must be present exactly when player and spawn dimensions match");
    }
    difficulty = ModelValidation.nonBlank(difficulty, "difficulty");
    if (!DIFFICULTIES.contains(difficulty)) {
      throw new IllegalArgumentException("difficulty is not supported: " + difficulty);
    }
    if (gameTimeTicks < 0) {
      throw new IllegalArgumentException("gameTimeTicks must not be negative");
    }
    if (worldDay != Math.floorDiv(dayTimeTicks, TICKS_PER_DAY)) {
      throw new IllegalArgumentException("worldDay must be derived from dayTimeTicks");
    }
    if (timeOfDayTicks != Math.floorMod(dayTimeTicks, TICKS_PER_DAY)) {
      throw new IllegalArgumentException("timeOfDayTicks must be derived from dayTimeTicks");
    }
    Objects.requireNonNull(daylightState, "daylightState");
    if (thundering && !raining) {
      throw new IllegalArgumentException("thundering requires raining");
    }
    if (localLightLevel < 0 || localLightLevel > 15) {
      throw new IllegalArgumentException("localLightLevel must be between 0 and 15");
    }
    moonPhase = ModelValidation.nonBlank(moonPhase, "moonPhase");
    if (!MOON_PHASES.contains(moonPhase)) {
      throw new IllegalArgumentException("moonPhase is not supported: " + moonPhase);
    }
    ModelValidation.finite(biomeTemperature, "biomeTemperature");
  }
}
