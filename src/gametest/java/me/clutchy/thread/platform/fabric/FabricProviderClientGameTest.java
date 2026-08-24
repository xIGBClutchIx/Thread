package me.clutchy.thread.platform.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.core.tool.ToolResult;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/** End-to-end proof that the vanilla catalog reads a real integrated game session. */
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
    ToolRegistry tools = entrypoint.tools();

    assertEquals(10, tools.descriptors().size(), "registered vanilla tool count");
    JsonObject menuStatus = invokeSuccessfully(context, tools, "minecraft.get_status", "{}");
    assertEquals("MAIN_MENU", menuStatus.get("state").getAsString(), "menu status");
    assertEquals(
        "26.2",
        invokeSuccessfully(context, tools, "minecraft.get_game_info", "{}")
            .get("minecraftVersion")
            .getAsString(),
        "live Minecraft version");
    assertEquals(
        10,
        invokeSuccessfully(context, tools, "minecraft.get_capabilities", "{}")
            .getAsJsonArray("tools")
            .size(),
        "live capability catalog");

    try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
      singleplayer.getConnection().waitForChunksDownload();
      singleplayer.getServer().runCommand("give @a minecraft:diamond 3");
      singleplayer.getConnection().waitForClientboundPackets();
      context.waitTick();

      assertTrue(
          invokeSuccessfully(context, tools, "minecraft.get_status", "{}")
              .get("supported")
              .getAsBoolean(),
          "single-player support");

      JsonObject player = invokeSuccessfully(context, tools, "minecraft.get_player", "{}");
      assertEquals(
          "minecraft:overworld", player.get("dimension").getAsString(), "player dimension");

      JsonObject recipes =
          invokeSuccessfully(
              context, tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
      assertTrue(!recipes.getAsJsonArray("recipes").isEmpty(), "diamond pickaxe recipe present");

      JsonObject search =
          invokeSuccessfully(
              context,
              tools,
              "minecraft.search_items",
              "{\"query\":\"diamond pick\",\"limit\":10}");
      assertTrue(
          search.getAsJsonArray("items").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(
                  item -> item.get("itemId").getAsString().equals("minecraft:diamond_pickaxe")),
          "live item registry search");

      invokeSuccessfully(context, tools, "minecraft.get_equipment", "{}");
      ToolResult<JsonElement> target = invoke(context, tools, "minecraft.get_target_block", "{}");
      assertTrue(
          target.successful() || target.error().code() == ToolErrorCode.NOT_FOUND,
          "target-block result");
      invokeSuccessfully(
          context, tools, "minecraft.get_nearby_entities", "{\"radius\":16,\"limit\":8}");

      JsonObject inventory = invokeSuccessfully(context, tools, "minecraft.get_inventory", "{}");
      assertTrue(
          inventory.getAsJsonArray("slots").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .map(slot -> slot.getAsJsonObject("stack"))
              .anyMatch(
                  stack ->
                      stack.get("itemId").getAsString().equals("minecraft:diamond")
                          && stack.get("count").getAsInt() == 3),
          "inventory preserves registry ID and count");
    }
  }

  private static JsonObject invokeSuccessfully(
      ClientGameTestContext context, ToolRegistry tools, String toolId, String input) {
    ToolResult<JsonElement> result = invoke(context, tools, toolId, input);
    if (!result.successful()) {
      throw new AssertionError(toolId + " failed: " + result.error());
    }
    if (!result.value().isJsonObject()) {
      throw new AssertionError(toolId + " returned a non-object result");
    }
    return result.value().getAsJsonObject();
  }

  private static ToolResult<JsonElement> invoke(
      ClientGameTestContext context, ToolRegistry tools, String toolId, String input) {
    JsonElement parsedInput = JsonParser.parseString(input);
    return awaitExternal(context, () -> tools.invoke(toolId, parsedInput));
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
