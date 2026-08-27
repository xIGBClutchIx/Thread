package me.clutchy.thread.platform.minecraft.game;

import java.util.Optional;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import net.minecraft.client.Minecraft;

/** Central V1 guard that blocks gameplay state outside supported single-player sessions. */
public final class MinecraftSessionGuard {
  /**
   * Returns the reason gameplay state cannot be exposed, or empty when the session is supported.
   */
  public Optional<ToolError> gameplayUnavailable(Minecraft client) {
    boolean worldLoaded = client.level != null;
    return gameplayUnavailable(
        worldLoaded,
        client.player != null,
        client.hasSingleplayerServer(),
        MinecraftSessionBinding.isMultiplayer(client, worldLoaded));
  }

  static Optional<ToolError> gameplayUnavailable(
      boolean worldLoaded,
      boolean playerAvailable,
      boolean integratedServerAvailable,
      boolean multiplayer) {
    if (!worldLoaded) {
      return Optional.of(
          ToolError.of(
              ToolErrorCode.WORLD_NOT_AVAILABLE,
              "No Minecraft world is currently available.",
              true));
    }
    if (!integratedServerAvailable || multiplayer) {
      return Optional.of(
          ToolError.of(
              ToolErrorCode.UNSUPPORTED,
              "Live gameplay tools are unavailable in multiplayer sessions.",
              true));
    }
    if (!playerAvailable) {
      return Optional.of(
          ToolError.of(
              ToolErrorCode.PLAYER_NOT_AVAILABLE, "No local player is currently available.", true));
    }
    return Optional.empty();
  }
}
