package me.clutchy.thread.platform.minecraft.game;

import net.minecraft.client.Minecraft;

/** Isolates the Minecraft 1.21.11 API used to classify a loaded multiplayer session. */
final class MinecraftSessionBinding {
  private MinecraftSessionBinding() {}

  static boolean isMultiplayer(Minecraft client, boolean worldLoaded) {
    return worldLoaded && !client.isSingleplayer();
  }
}
