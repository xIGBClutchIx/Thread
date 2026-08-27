package me.clutchy.thread.minecraft.v26_1_2;

import me.clutchy.thread.core.tool.VersionCapabilities;
import me.clutchy.thread.minecraft.MinecraftCapabilities;

/** Built-in tool support implemented by the Minecraft 26.1.2 adapter. */
public final class Minecraft2612Capabilities {
  private Minecraft2612Capabilities() {}

  /** Returns the support map proven by the Minecraft 26.1.2 adapter tests. */
  public static VersionCapabilities capabilities() {
    return MinecraftCapabilities.fullySupported();
  }
}
