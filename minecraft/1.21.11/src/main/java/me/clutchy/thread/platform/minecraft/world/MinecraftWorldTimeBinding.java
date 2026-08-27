package me.clutchy.thread.platform.minecraft.world;

import net.minecraft.server.level.ServerLevel;

/** Resolves the ordinary day clock exposed by Minecraft 1.21.11. */
final class MinecraftWorldTimeBinding {
  private MinecraftWorldTimeBinding() {}

  static long dayTimeTicks(ServerLevel level) {
    return level.getDayTime();
  }
}
