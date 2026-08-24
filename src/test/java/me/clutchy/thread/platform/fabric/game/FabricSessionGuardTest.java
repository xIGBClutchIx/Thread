package me.clutchy.thread.platform.fabric.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.clutchy.thread.core.error.ToolErrorCode;
import org.junit.jupiter.api.Test;

class FabricSessionGuardTest {
  @Test
  void reportsDefinedErrorsForUnavailableGameplayState() {
    assertEquals(
        ToolErrorCode.WORLD_NOT_AVAILABLE,
        FabricSessionGuard.gameplayUnavailable(false, false, false, false).orElseThrow().code());
    assertEquals(
        ToolErrorCode.UNSUPPORTED,
        FabricSessionGuard.gameplayUnavailable(true, true, false, true).orElseThrow().code());
    assertEquals(
        ToolErrorCode.PLAYER_NOT_AVAILABLE,
        FabricSessionGuard.gameplayUnavailable(true, false, true, false).orElseThrow().code());
    assertTrue(FabricSessionGuard.gameplayUnavailable(true, true, true, false).isEmpty());
  }
}
