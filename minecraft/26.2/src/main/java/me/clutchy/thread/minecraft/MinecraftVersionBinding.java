package me.clutchy.thread.minecraft;

import me.clutchy.thread.core.tool.VersionCapabilities;
import me.clutchy.thread.minecraft.v26_2.Minecraft262Capabilities;

/** Compile-time binding between shared Minecraft assembly and the 26.2 capability identity. */
final class MinecraftVersionBinding {
  private MinecraftVersionBinding() {}

  static VersionCapabilities capabilities() {
    return Minecraft262Capabilities.capabilities();
  }
}
