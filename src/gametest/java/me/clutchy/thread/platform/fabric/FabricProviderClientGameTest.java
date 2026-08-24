package me.clutchy.thread.platform.fabric;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import me.clutchy.thread.core.model.InventorySlotInfo;
import me.clutchy.thread.core.model.NearbyEntityQuery;
import me.clutchy.thread.core.model.RecipeInfo;
import me.clutchy.thread.core.model.SessionState;
import me.clutchy.thread.core.tool.ToolResult;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/** End-to-end proof that Slice 2 providers read a real integrated game session. */
@SuppressWarnings("UnstableApiUsage")
public final class FabricProviderClientGameTest implements FabricClientGameTest {
  @Override
  public void runTest(ClientGameTestContext context) {
    ThreadFabricClient entrypoint =
        FabricLoader.getInstance()
            .getEntrypointContainers("client", ClientModInitializer.class)
            .stream()
            .filter(container -> container.getProvider().getMetadata().getId().equals("thread"))
            .map(container -> (ThreadFabricClient) container.getEntrypoint())
            .findFirst()
            .orElseThrow();
    FabricProviderBundle providers = entrypoint.providers();

    assertEquals(
        SessionState.MAIN_MENU,
        awaitExternal(context, providers.game()::sessionStatus).state(),
        "menu status");
    try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
      singleplayer.getConnection().waitForChunksDownload();
      singleplayer.getServer().runCommand("give @a minecraft:diamond 3");
      singleplayer.getConnection().waitForClientboundPackets();
      context.waitTick();

      assertTrue(
          awaitExternal(context, providers.game()::sessionStatus).supported(),
          "single-player support");
      assertTrue(awaitExternal(context, providers.player()::status).successful(), "player status");
      ToolResult<List<RecipeInfo>> recipes =
          awaitExternal(context, () -> providers.recipe().recipesFor("minecraft:diamond_pickaxe"));
      assertTrue(recipes.successful(), "live recipe lookup");
      assertTrue(!recipes.value().isEmpty(), "diamond pickaxe recipe present");
      assertTrue(
          awaitExternal(context, () -> providers.recipe().searchItems("diamond pick", 10))
              .successful(),
          "item registry search");
      assertTrue(
          awaitExternal(context, providers.player()::equipment).successful(), "equipment snapshot");
      assertTrue(
          awaitExternal(context, providers.player()::targetBlock).successful(),
          "target-block snapshot");
      assertTrue(
          awaitExternal(
                  context, () -> providers.world().nearbyEntities(new NearbyEntityQuery(16, 8)))
              .successful(),
          "bounded entity query");

      List<InventorySlotInfo> slots =
          awaitExternal(context, providers.player()::inventory).value().slots();
      assertTrue(
          slots.stream()
              .anyMatch(
                  slot ->
                      slot.stack().itemId().equals("minecraft:diamond")
                          && slot.stack().count() == 3),
          "inventory preserves registry ID and count");
    }
  }

  private static <T> T awaitExternal(ClientGameTestContext context, Supplier<T> operation) {
    CompletableFuture<T> result = CompletableFuture.supplyAsync(operation);
    for (int elapsedTicks = 0; elapsedTicks < 200 && !result.isDone(); elapsedTicks++) {
      // Client game tests pause both logical game threads unless the test advances a tick. Pumping
      // ticks here preserves the production behavior under test: the operation itself starts on a
      // non-game thread and uses Thread's dispatchers to reach the owning client/server thread.
      context.waitTick();
    }
    if (!result.isDone()) {
      throw new AssertionError("external provider call did not finish within 200 game ticks");
    }
    return result.join();
  }

  private static void assertTrue(boolean condition, String description) {
    if (!condition) {
      throw new AssertionError(description);
    }
  }

  private static void assertEquals(Object expected, Object actual, String description) {
    if (!expected.equals(actual)) {
      throw new AssertionError(description + ": expected " + expected + " but was " + actual);
    }
  }
}
