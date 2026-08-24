package me.clutchy.thread.platform.fabric.game;

/** Hard safety bounds used by live providers until Slice 5 makes them configurable. */
public record FabricProviderLimits(
    double maxEntityRadius,
    int maxEntityResults,
    int maxItemDefinitions,
    int maxItemSearchResults,
    int maxRecipeDefinitions,
    int maxRecipesPerItem) {
  public FabricProviderLimits {
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

  /** Returns conservative V1 limits for loaded-state queries. */
  public static FabricProviderLimits defaults() {
    return new FabricProviderLimits(64, 128, 8_192, 64, 16_384, 256);
  }
}
