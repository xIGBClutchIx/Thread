package me.clutchy.thread.platform.minecraft.world;

import net.minecraft.server.level.ServerLevel;

/** Resolves the overworld clock introduced by Minecraft 26.1.2. */
final class MinecraftWorldTimeBinding {
  private MinecraftWorldTimeBinding() {}

  static long dayTimeTicks(ServerLevel level) {
    return level.getOverworldClockTime();
  }
}
