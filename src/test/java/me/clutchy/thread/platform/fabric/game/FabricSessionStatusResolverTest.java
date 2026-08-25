package me.clutchy.thread.platform.fabric.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.clutchy.thread.core.model.game.SessionState;
import me.clutchy.thread.core.model.game.SessionStatus;
import me.clutchy.thread.core.model.game.SessionStatusReason;
import org.junit.jupiter.api.Test;

class FabricSessionStatusResolverTest {
  @Test
  void statusRemainsAvailableWithoutAWorld() {
    SessionStatus status = FabricSessionStatusResolver.resolve(false, false, false, false);

    assertEquals(SessionState.MAIN_MENU, status.state());
    assertFalse(status.worldLoaded());
    assertFalse(status.playerAvailable());
    assertFalse(status.supported());
    assertEquals(SessionStatusReason.NO_WORLD, status.reason());
  }

  @Test
  void distinguishesLoadingUnsupportedAndSupportedSessions() {
    SessionStatus loading = FabricSessionStatusResolver.resolve(false, false, true, false);
    SessionStatus multiplayer = FabricSessionStatusResolver.resolve(true, true, false, true);
    SessionStatus missingPlayer = FabricSessionStatusResolver.resolve(true, false, true, false);
    SessionStatus singleplayer = FabricSessionStatusResolver.resolve(true, true, true, false);

    assertEquals(SessionState.LOADING_WORLD, loading.state());
    assertEquals(SessionStatusReason.WORLD_LOADING, loading.reason());
    assertEquals(SessionState.MULTIPLAYER, multiplayer.state());
    assertEquals(SessionStatusReason.MULTIPLAYER_UNSUPPORTED, multiplayer.reason());
    assertEquals(SessionState.SINGLEPLAYER, missingPlayer.state());
    assertEquals(SessionStatusReason.PLAYER_NOT_AVAILABLE, missingPlayer.reason());
    assertTrue(singleplayer.supported());
    assertNull(singleplayer.reason());
  }
}
