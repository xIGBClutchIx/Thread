package me.clutchy.thread.minecraft;

import me.clutchy.thread.core.tool.VersionCapabilities;
import me.clutchy.thread.minecraft.v1_21_11.Minecraft12111Capabilities;

/** Compile-time binding between shared Minecraft assembly and the 1.21.11 capability identity. */
final class MinecraftVersionBinding {
  private MinecraftVersionBinding() {}

  static VersionCapabilities capabilities() {
    return Minecraft12111Capabilities.capabilities();
  }
}
