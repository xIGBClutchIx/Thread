package me.clutchy.thread.platform.fabric.testing;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Completes the static built-in registry phase normally owned by game startup. */
public final class MinecraftTestBootstrap {
  private static boolean initialized;

  private MinecraftTestBootstrap() {}

  /** Initializes Minecraft's static registry state once for platform unit tests. */
  public static synchronized void initialize() {
    if (initialized) {
      return;
    }
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    initialized = true;
  }
}
