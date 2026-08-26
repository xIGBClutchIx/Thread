package me.clutchy.thread.platform.minecraft.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MinecraftProviderLimitsTest {
  @Test
  void defaultsBoundEveryPotentiallyLargeProviderRead() {
    MinecraftProviderLimits limits = MinecraftProviderLimits.defaults();

    assertEquals(64, limits.maxEntityRadius());
    assertEquals(128, limits.maxEntityResults());
    assertEquals(8_192, limits.maxItemDefinitions());
    assertEquals(64, limits.maxItemSearchResults());
    assertEquals(16_384, limits.maxRecipeDefinitions());
    assertEquals(256, limits.maxRecipesPerItem());
  }

  @Test
  void rejectsNonPositiveBounds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MinecraftProviderLimits(0, 128, 8_192, 64, 16_384, 256));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MinecraftProviderLimits(64, 128, 8_192, 64, 0, 256));
  }

  @Test
  void configuredLimitsPreserveFixedRegistryCeilings() {
    MinecraftProviderLimits limits = MinecraftProviderLimits.configured(24, 20, 10);

    assertEquals(24, limits.maxEntityRadius());
    assertEquals(20, limits.maxEntityResults());
    assertEquals(10, limits.maxItemSearchResults());
    assertEquals(
        MinecraftProviderLimits.defaults().maxItemDefinitions(), limits.maxItemDefinitions());
    assertEquals(
        MinecraftProviderLimits.defaults().maxRecipeDefinitions(), limits.maxRecipeDefinitions());
  }
}
