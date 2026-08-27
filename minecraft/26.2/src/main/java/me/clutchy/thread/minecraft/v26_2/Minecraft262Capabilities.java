package me.clutchy.thread.minecraft.v26_2;

import me.clutchy.thread.core.tool.VersionCapabilities;
import me.clutchy.thread.minecraft.MinecraftCapabilities;

/** Complete built-in tool support for the Minecraft 26.2 adapter. */
public final class Minecraft262Capabilities {
  private Minecraft262Capabilities() {}

  /** Returns the exact twenty-one-tool support map for Minecraft 26.2. */
  public static VersionCapabilities capabilities() {
    return MinecraftCapabilities.fullySupported();
  }
}
