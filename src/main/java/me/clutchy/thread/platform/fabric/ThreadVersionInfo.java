package me.clutchy.thread.platform.fabric;

import java.util.Objects;
import me.clutchy.thread.core.model.GameInfo;

/** Snapshot of runtime component versions used in Thread's startup log. */
record ThreadVersionInfo(String threadVersion, String minecraftVersion, String loaderVersion) {
  ThreadVersionInfo {
    Objects.requireNonNull(threadVersion, "threadVersion");
    Objects.requireNonNull(minecraftVersion, "minecraftVersion");
    Objects.requireNonNull(loaderVersion, "loaderVersion");
  }

  String startupMessage() {
    return "Thread %s initialized for Minecraft %s with Fabric Loader %s"
        .formatted(threadVersion, minecraftVersion, loaderVersion);
  }

  GameInfo gameInfo() {
    return new GameInfo(minecraftVersion, "fabric", loaderVersion, threadVersion);
  }
}
