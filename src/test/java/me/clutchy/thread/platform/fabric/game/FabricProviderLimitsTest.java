package me.clutchy.thread.platform.fabric.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FabricProviderLimitsTest {
  @Test
  void defaultsBoundEveryPotentiallyLargeProviderRead() {
    FabricProviderLimits limits = FabricProviderLimits.defaults();

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
        () -> new FabricProviderLimits(0, 128, 8_192, 64, 16_384, 256));
    assertThrows(
        IllegalArgumentException.class, () -> new FabricProviderLimits(64, 128, 8_192, 64, 0, 256));
  }
}
