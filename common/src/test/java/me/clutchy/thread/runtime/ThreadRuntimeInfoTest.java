package me.clutchy.thread.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ThreadRuntimeInfoTest {
  @Test
  void exposesLoaderNeutralGameInfoAndStartupMessage() {
    ThreadRuntimeInfo info =
        new ThreadRuntimeInfo("0.1.0", "26.2", "fabric", "Fabric Loader", "0.19.3");

    assertEquals(
        "Thread 0.1.0 initialized for Minecraft 26.2 with Fabric Loader 0.19.3",
        info.startupMessage());
    assertEquals("26.2", info.gameInfo().minecraftVersion());
    assertEquals("fabric", info.gameInfo().loader());
    assertEquals("0.19.3", info.gameInfo().loaderVersion());
    assertEquals("0.1.0", info.gameInfo().threadVersion());
  }
}
