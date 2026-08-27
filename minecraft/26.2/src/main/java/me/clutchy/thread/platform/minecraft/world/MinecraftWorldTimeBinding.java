package me.clutchy.thread.platform.minecraft.world;

import net.minecraft.server.level.ServerLevel;

/** Resolves the overworld clock used by Minecraft 26.2. */
final class MinecraftWorldTimeBinding {
  private MinecraftWorldTimeBinding() {}

  static long dayTimeTicks(ServerLevel level) {
    return level.getOverworldClockTime();
  }
}
