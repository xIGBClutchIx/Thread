package me.clutchy.thread.minecraft.v1_21_11;

import me.clutchy.thread.core.tool.VersionCapabilities;
import me.clutchy.thread.minecraft.MinecraftCapabilities;

/** Built-in tool support implemented by the Minecraft 1.21.11 adapter. */
public final class Minecraft12111Capabilities {
  private Minecraft12111Capabilities() {}

  /** Returns the support map proven by the Minecraft 1.21.11 adapter tests. */
  public static VersionCapabilities capabilities() {
    return MinecraftCapabilities.fullySupported();
  }
}
