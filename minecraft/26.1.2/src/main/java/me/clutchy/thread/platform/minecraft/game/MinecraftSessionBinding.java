package me.clutchy.thread.platform.minecraft.game;

import net.minecraft.client.Minecraft;

/** Isolates the Minecraft 26.1.2 API used to classify a loaded multiplayer session. */
final class MinecraftSessionBinding {
  private MinecraftSessionBinding() {}

  static boolean isMultiplayer(Minecraft client, boolean worldLoaded) {
    // 26.1.2 keeps isMultiplayerServer private. The public inverse preserves LAN-published
    // integrated-server rejection instead of inferring multiplayer from connection details.
    return worldLoaded && !client.isSingleplayer();
  }
}
