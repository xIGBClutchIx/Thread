package me.clutchy.thread.platform.minecraft.game;

import me.clutchy.thread.core.model.world.NearbyContainerQuery;

/** Server-enforced safety bounds used by live Minecraft providers. */
public record MinecraftProviderLimits(
    double maxEntityRadius,
    int maxEntityResults,
    int maxItemDefinitions,
    int maxItemSearchResults,
    int maxRecipeDefinitions,
    int maxRecipesPerItem) {
  public MinecraftProviderLimits {
    if (!Double.isFinite(maxEntityRadius) || maxEntityRadius <= 0) {
      throw new IllegalArgumentException("maxEntityRadius must be finite and positive");
    }
    if (maxEntityResults <= 0) {
      throw new IllegalArgumentException("maxEntityResults must be positive");
    }
    if (maxItemSearchResults <= 0) {
      throw new IllegalArgumentException("maxItemSearchResults must be positive");
    }
    if (maxItemDefinitions <= 0) {
      throw new IllegalArgumentException("maxItemDefinitions must be positive");
    }
    if (maxRecipeDefinitions <= 0) {
      throw new IllegalArgumentException("maxRecipeDefinitions must be positive");
    }
    if (maxRecipesPerItem <= 0) {
      throw new IllegalArgumentException("maxRecipesPerItem must be positive");
    }
  }

  /** Returns conservative Thread limits for loaded-state queries. */
  public static MinecraftProviderLimits defaults() {
    return new MinecraftProviderLimits(64, 128, 8_192, 64, 16_384, 256);
  }

  /** Returns configured player-facing limits while preserving fixed internal registry ceilings. */
  public static MinecraftProviderLimits configured(
      double maxEntityRadius, int maxEntityResults, int maxItemSearchResults) {
    return new MinecraftProviderLimits(
        maxEntityRadius, maxEntityResults, 8_192, maxItemSearchResults, 16_384, 256);
  }

  /**
   * Returns the container scan radius after applying the stricter fixed workload ceiling.
   *
   * <p>Container discovery visits block positions rather than an entity index, so it intentionally
   * uses a smaller ceiling while still respecting a user-configured lower world-query radius.
   */
  public double maxContainerRadius() {
    return Math.min(maxEntityRadius, NearbyContainerQuery.HARD_MAX_RADIUS);
  }

  /** Returns the container result cap after respecting the configured world-query result limit. */
  public int maxContainerResults() {
    return Math.min(maxEntityResults, NearbyContainerQuery.HARD_MAX_RESULTS);
  }
}
