package me.clutchy.thread.platform.minecraft.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.clutchy.thread.core.error.ToolErrorCode;
import org.junit.jupiter.api.Test;

class MinecraftSessionGuardTest {
  @Test
  void reportsDefinedErrorsForUnavailableGameplayState() {
    assertEquals(
        ToolErrorCode.WORLD_NOT_AVAILABLE,
        MinecraftSessionGuard.gameplayUnavailable(false, false, false, false).orElseThrow().code());
    assertEquals(
        ToolErrorCode.UNSUPPORTED,
        MinecraftSessionGuard.gameplayUnavailable(true, true, false, true).orElseThrow().code());
    assertEquals(
        ToolErrorCode.PLAYER_NOT_AVAILABLE,
        MinecraftSessionGuard.gameplayUnavailable(true, false, true, false).orElseThrow().code());
    assertTrue(MinecraftSessionGuard.gameplayUnavailable(true, true, true, false).isEmpty());
  }
}
