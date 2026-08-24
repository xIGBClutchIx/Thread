package me.clutchy.thread.platform.fabric.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.NearbyEntityQuery;
import me.clutchy.thread.platform.fabric.game.FabricProviderLimits;
import org.junit.jupiter.api.Test;

class FabricWorldProviderTest {
  private static final FabricProviderLimits LIMITS = FabricProviderLimits.defaults();

  @Test
  void enforcesRadiusAndResultCapsBeforeGameThreadDispatch() {
    assertEquals(
        ToolErrorCode.OUT_OF_RANGE,
        FabricWorldProvider.validateQuery(new NearbyEntityQuery(65, 64), LIMITS)
            .orElseThrow()
            .code());
    assertEquals(
        ToolErrorCode.RESULT_LIMIT_EXCEEDED,
        FabricWorldProvider.validateQuery(new NearbyEntityQuery(16, 129), LIMITS)
            .orElseThrow()
            .code());
    assertTrue(FabricWorldProvider.validateQuery(new NearbyEntityQuery(16, 64), LIMITS).isEmpty());
  }
}
