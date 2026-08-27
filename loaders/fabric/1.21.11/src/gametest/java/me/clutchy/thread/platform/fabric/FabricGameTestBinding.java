package me.clutchy.thread.platform.fabric;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Compile-time binding for Fabric API's Minecraft 1.21.11 client-world test context. */
final class FabricGameTestBinding {
  private FabricGameTestBinding() {}

  static void waitForChunksDownload(TestSingleplayerContext context) {
    context.getClientWorld().waitForChunksDownload();
  }
}
