package me.clutchy.thread.platform.fabric;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Compile-time binding for Fabric API's Minecraft 26.2 client-level test context. */
final class FabricGameTestBinding {
  private FabricGameTestBinding() {}

  static void waitForChunksDownload(TestSingleplayerContext context) {
    context.getClientLevel().waitForChunksDownload();
  }
}
