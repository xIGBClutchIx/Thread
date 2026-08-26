package me.clutchy.thread.platform.minecraft.game;

import me.clutchy.thread.core.model.game.SessionState;
import me.clutchy.thread.core.model.game.SessionStatus;
import me.clutchy.thread.core.model.game.SessionStatusReason;

/** Pure session-state mapping kept separate from Minecraft's mutable client object. */
final class MinecraftSessionStatusResolver {
  private MinecraftSessionStatusResolver() {}

  static SessionStatus resolve(
      boolean worldLoaded,
      boolean playerAvailable,
      boolean integratedServerAvailable,
      boolean multiplayer) {
    if (!worldLoaded) {
      if (integratedServerAvailable) {
        return new SessionStatus(
            SessionState.LOADING_WORLD,
            false,
            playerAvailable,
            false,
            SessionStatusReason.WORLD_LOADING);
      }
      return new SessionStatus(
          SessionState.MAIN_MENU, false, playerAvailable, false, SessionStatusReason.NO_WORLD);
    }
    if (!integratedServerAvailable || multiplayer) {
      return new SessionStatus(
          SessionState.MULTIPLAYER,
          true,
          playerAvailable,
          false,
          SessionStatusReason.MULTIPLAYER_UNSUPPORTED);
    }
    if (!playerAvailable) {
      return new SessionStatus(
          SessionState.SINGLEPLAYER, true, false, false, SessionStatusReason.PLAYER_NOT_AVAILABLE);
    }
    return new SessionStatus(SessionState.SINGLEPLAYER, true, true, true, null);
  }
}
