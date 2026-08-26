package me.clutchy.thread.platform.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.platform.minecraft.game.MinecraftProviderLimits;
import org.junit.jupiter.api.Test;

class MinecraftWorldProviderTest {
  private static final MinecraftProviderLimits LIMITS = MinecraftProviderLimits.defaults();

  @Test
  void enforcesRadiusAndResultCapsBeforeGameThreadDispatch() {
    assertEquals(
        ToolErrorCode.OUT_OF_RANGE,
        MinecraftWorldProvider.validateQuery(new NearbyEntityQuery(65, 64), LIMITS)
            .orElseThrow()
            .code());
    assertEquals(
        ToolErrorCode.RESULT_LIMIT_EXCEEDED,
        MinecraftWorldProvider.validateQuery(new NearbyEntityQuery(16, 129), LIMITS)
            .orElseThrow()
            .code());
    assertTrue(
        MinecraftWorldProvider.validateQuery(new NearbyEntityQuery(16, 64), LIMITS).isEmpty());
  }
}
