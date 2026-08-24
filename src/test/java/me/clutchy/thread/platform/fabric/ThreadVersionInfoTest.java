package me.clutchy.thread.platform.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ThreadVersionInfoTest {
  @Test
  void startupMessageNamesEveryPinnedRuntimeComponent() {
    ThreadVersionInfo versions = new ThreadVersionInfo("0.1.0", "26.2", "0.19.3");

    assertEquals(
        "Thread 0.1.0 initialized for Minecraft 26.2 with Fabric Loader 0.19.3",
        versions.startupMessage());
    assertEquals("26.2", versions.gameInfo().minecraftVersion());
    assertEquals("fabric", versions.gameInfo().loader());
    assertEquals("0.19.3", versions.gameInfo().loaderVersion());
    assertEquals("0.1.0", versions.gameInfo().threadVersion());
  }
}
