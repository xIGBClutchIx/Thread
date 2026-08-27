package me.clutchy.thread.minecraft;

import me.clutchy.thread.core.tool.VersionCapabilities;
import me.clutchy.thread.minecraft.v26_1_2.Minecraft2612Capabilities;

/** Compile-time binding between shared Minecraft assembly and the 26.1.2 capability identity. */
final class MinecraftVersionBinding {
  private MinecraftVersionBinding() {}

  static VersionCapabilities capabilities() {
    return Minecraft2612Capabilities.capabilities();
  }
}
