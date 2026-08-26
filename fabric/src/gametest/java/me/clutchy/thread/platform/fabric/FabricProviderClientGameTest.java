package me.clutchy.thread.platform.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.config.ThreadConfigLoader;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.fabric.integration.ExternalProofIntegration;
import me.clutchy.thread.transport.mcp.McpHttpServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

/** End-to-end proof of native gameplay reads and the external-mod integration bridge. */
@SuppressWarnings("UnstableApiUsage")
public final class FabricProviderClientGameTest implements FabricClientGameTest {
  private static final String NATIVE_COMMAND_BLOCK_RECIPE =
      "thread:native_command_block_from_earth";
  private static final String NATIVE_CHAIN_COMMAND_BLOCK_RECIPE =
      "thread:native_chain_command_block_from_command_block";
  private static final String EXPECT_MCP_DISABLED = "thread.gametest.expectMcpDisabled";
  private static final String EXPECTED_MCP_PORT = "thread.gametest.expectedMcpPort";

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
    ThreadConfig expectedConfig = expectedConfig();
    assertEquals(expectedConfig, readRuntimeConfig(), "persisted runtime configuration");
    assertEquals(2, entrypoint.integrations().integrations().size(), "active integration count");
    assertEquals(
        "external-fabric-entrypoint",
        entrypoint.integrations().integrations().stream()
            .filter(integration -> integration.id().value().equals("gametest-bridge"))
            .findFirst()
            .orElseThrow()
            .metadata()
            .get("gametest.source"),
        "external candidate provider registration");
    assertEquals(
        "13",
        entrypoint.integrations().integrations().stream()
            .filter(integration -> integration.id().value().equals("vanilla"))
            .findFirst()
            .orElseThrow()
            .metadata()
            .get("thread.tool_count"),
        "vanilla contribution metadata");
    if (Boolean.getBoolean(EXPECT_MCP_DISABLED)) {
      assertTrue(!entrypoint.mcpRunning(), "MCP remains stopped when configured off");
      assertEquals(13, tools.descriptors().size(), "tools initialize independently of MCP");
      return;
    }
    McpHttpServer mcp = entrypoint.mcpServer();

    assertEquals(13, tools.descriptors().size(), "registered vanilla tool count");
    JsonObject menuStatus = invokeSuccessfully(context, tools, "minecraft.get_status", "{}");
    assertEquals("MAIN_MENU", menuStatus.get("state").getAsString(), "menu status");
    assertEquals(
        "26.2",
        invokeSuccessfully(context, tools, "minecraft.get_game_info", "{}")
            .get("minecraftVersion")
            .getAsString(),
        "live Minecraft version");
    assertEquals(
        13,
        invokeSuccessfully(context, tools, "minecraft.get_capabilities", "{}")
            .getAsJsonArray("tools")
            .size(),
        "live capability catalog");

    assertTrue(mcp.running(), "MCP listener running");
    assertEquals(expectedConfig.mcpPort(), mcp.endpoint().getPort(), "configured MCP port");
    JsonObject initialize = mcpInitialize(context, mcp.endpoint(), 0).body();
    assertEquals(
        "2026-07-28",
        initialize.getAsJsonObject("result").get("protocolVersion").getAsString(),
        "MCP initialize protocol");
    assertEquals(
        "Thread",
        initialize
            .getAsJsonObject("result")
            .getAsJsonObject("serverInfo")
            .get("name")
            .getAsString(),
        "MCP initialize server identity");
    McpResponse initialized = mcpInitialized(context, mcp.endpoint());
    assertEquals(202, initialized.status(), "MCP initialized notification");
    assertTrue(initialized.body() == null, "MCP initialized notification has no response body");
    JsonObject discovery =
        mcpRequest(context, mcp.endpoint(), 1, "server/discover", null, null).body();
    assertEquals(
        "2026-07-28",
        discovery
            .getAsJsonObject("result")
            .getAsJsonArray("supportedVersions")
            .get(0)
            .getAsString(),
        "MCP protocol discovery");
    JsonObject catalog = mcpRequest(context, mcp.endpoint(), 2, "tools/list", null, null).body();
    assertEquals(
        13,
        catalog.getAsJsonObject("result").getAsJsonArray("tools").size(),
        "MCP vanilla catalog");
    assertEquals(
        "MAIN_MENU",
        mcpTool(context, mcp.endpoint(), 3, "minecraft.get_status", new JsonObject())
            .get("state")
            .getAsString(),
        "MCP menu status");
    assertEquals(
        "26.2",
        mcpTool(context, mcp.endpoint(), 4, "minecraft.get_game_info", new JsonObject())
            .get("minecraftVersion")
            .getAsString(),
        "MCP live Minecraft version");

    McpResponse missing =
        mcpRequest(context, mcp.endpoint(), 5, "tools/call", "minecraft.missing", new JsonObject());
    assertEquals(400, missing.status(), "unknown MCP tool status");
    assertEquals(
        -32_602,
        missing.body().getAsJsonObject("error").get("code").getAsInt(),
        "unknown MCP tool error");
    assertTrue(mcp.running(), "invalid MCP call leaves listener running");

    assertToolError(
        mcpToolResult(context, mcp.endpoint(), 18, "minecraft.get_inventory", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu gameplay rejection");

    try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
      singleplayer.getClientLevel().waitForChunksDownload();
      singleplayer.getServer().runCommand("fill -3 99 -3 3 99 5 minecraft:stone");
      singleplayer
          .getServer()
          .runCommand("setblock 0 101 3 minecraft:furnace[facing=north,lit=false]");
      singleplayer
          .getServer()
          .runCommand("item replace block 0 101 3 container.0 with minecraft:iron_ore 3");
      singleplayer
          .getServer()
          .runCommand("item replace block 0 101 3 container.1 with minecraft:coal");
      singleplayer.getServer().runCommand("tp @a 0.5 100 0.5 0 0");
      singleplayer
          .getServer()
          .runCommand("item replace entity @a hotbar.0 with minecraft:diamond_pickaxe");
      singleplayer.getServer().runCommand("enchant @a minecraft:efficiency 5");
      singleplayer
          .getServer()
          .runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
      singleplayer.getServer().runCommand("give @a minecraft:diamond 3");
      singleplayer.getServer().runCommand("give @a minecraft:stick 2");
      singleplayer.getServer().runCommand("give @a minecraft:oak_log 1");
      singleplayer.getServer().runCommand("give @a minecraft:dirt 1");
      singleplayer.getServer().runCommand("summon minecraft:minecart 2 100 0");
      singleplayer
          .getServer()
          .runCommand("summon minecraft:zombie 4 100 0 {NoAI:1b,Silent:1b,Invulnerable:1b}");
      // Waiting for the command's observable client state keeps this packaged test deterministic
      // without depending on a game-test packet-drain convenience API.
      context.waitFor(
          client ->
              hasInventoryStack(client, Items.DIAMOND, 3)
                  && hasInventoryStack(client, Items.STICK, 2)
                  && hasInventoryStack(client, Items.OAK_LOG, 1)
                  && hasInventoryStack(client, Items.DIRT, 1)
                  && client.player != null
                  && client.player.getMainHandItem().is(Items.DIAMOND_PICKAXE));
      context.waitFor(FabricProviderClientGameTest::targetsKnownFurnace);

      assertTrue(
          invokeSuccessfully(context, tools, "minecraft.get_status", "{}")
              .get("supported")
              .getAsBoolean(),
          "single-player support");

      JsonObject unknownItem = new JsonObject();
      unknownItem.addProperty("itemId", "thread:missing_item");
      assertToolError(
          mcpToolResult(context, mcp.endpoint(), 21, "minecraft.get_recipe", unknownItem),
          "NOT_FOUND",
          "The requested item is not registered.",
          false,
          "unknown recipe item rejection");

      JsonObject player = invokeSuccessfully(context, tools, "minecraft.get_player", "{}");
      assertEquals(
          "minecraft:overworld", player.get("dimension").getAsString(), "player dimension");

      JsonObject recipes =
          invokeSuccessfully(
              context, tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
      assertTrue(!recipes.getAsJsonArray("recipes").isEmpty(), "diamond pickaxe recipe present");
      assertEquals(1, recipeOccurrences(recipes, "minecraft:dirt"), "datapack override dirt input");
      assertEquals(0, recipeOccurrences(recipes, "minecraft:diamond"), "replaced vanilla diamonds");
      assertEquals(0, recipeOccurrences(recipes, "minecraft:stick"), "replaced vanilla sticks");
      JsonObject directCraftability =
          invokeSuccessfully(
              context, tools, "minecraft.can_craft", "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
      assertTrue(directCraftability.get("craftable").getAsBoolean(), "direct craftability");
      JsonObject directMissing =
          invokeSuccessfully(
              context,
              tools,
              "minecraft.get_missing_ingredients",
              "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
      assertTrue(directMissing.get("craftable").getAsBoolean(), "direct missing ingredient counts");
      JsonObject directPlan =
          invokeSuccessfully(
              context,
              tools,
              "minecraft.get_crafting_plan",
              "{\"itemId\":\"minecraft:crafting_table\"}");
      assertTrue(directPlan.get("craftable").getAsBoolean(), "direct recursive crafting plan");
      assertTrue(directPlan.getAsJsonArray("steps").size() >= 2, "recursive intermediate steps");
      assertEquals(
          "minecraft:crafting_table",
          directPlan
              .getAsJsonArray("steps")
              .get(directPlan.getAsJsonArray("steps").size() - 1)
              .getAsJsonObject()
              .get("itemId")
              .getAsString(),
          "recursive plan final step");
      assertTrue(directPlan.getAsJsonArray("missingMaterials").isEmpty(), "no raw shortages");
      assertTrue(directPlan.getAsJsonArray("issues").isEmpty(), "no planning safety issues");

      verifyNativeRecipes(context, tools);
      assertTrue(
          ExternalProofIntegration.recipeLookups() > 0,
          "external recipe contribution receives live lookups");
      JsonObject nativeOnlyRecipes =
          invokeSuccessfully(
              context, tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:barrier\"}");
      assertTrue(
          nativeOnlyRecipes.getAsJsonArray("recipes").isEmpty(),
          "native provider does not invent recipes absent from the live manager");

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

      JsonObject directEquipment =
          invokeSuccessfully(context, tools, "minecraft.get_equipment", "{}");
      assertEquals(6, directEquipment.getAsJsonArray("slots").size(), "fixed equipment slot count");
      ToolResult<JsonElement> target = invoke(context, tools, "minecraft.get_target_block", "{}");
      assertTrue(target.successful(), "target-block result");
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

      JsonObject mcpStatus =
          mcpTool(context, mcp.endpoint(), 6, "minecraft.get_status", new JsonObject());
      assertTrue(mcpStatus.get("supported").getAsBoolean(), "MCP loaded-world support");
      assertEquals("SINGLEPLAYER", mcpStatus.get("state").getAsString(), "MCP single-player state");

      JsonObject mcpPlayer =
          mcpTool(context, mcp.endpoint(), 7, "minecraft.get_player", new JsonObject());
      assertEquals(
          "minecraft:overworld", mcpPlayer.get("dimension").getAsString(), "MCP player dimension");
      assertTrue(mcpPlayer.get("health").getAsDouble() > 0, "MCP health");
      assertTrue(mcpPlayer.get("food").getAsInt() > 0, "MCP hunger/food level");

      JsonObject mcpInventory =
          mcpTool(context, mcp.endpoint(), 8, "minecraft.get_inventory", new JsonObject());
      assertEquals(3, itemCount(mcpInventory, "minecraft:diamond"), "MCP diamond count");
      assertEquals(2, itemCount(mcpInventory, "minecraft:stick"), "MCP stick count");
      assertEquals(
          0,
          itemCount(mcpInventory, "minecraft:diamond_helmet"),
          "inventory excludes equipment slots");

      JsonObject mcpEquipment =
          mcpTool(context, mcp.endpoint(), 9, "minecraft.get_equipment", new JsonObject());
      JsonObject mainHand = equipmentItem(mcpEquipment, "MAIN_HAND");
      assertEquals(
          "minecraft:diamond_pickaxe",
          mainHand.get("itemId").getAsString(),
          "MCP main-hand equipment");
      assertTrue(
          mainHand.getAsJsonObject("durability").get("maximum").getAsInt() > 0, "durability");
      assertTrue(!mainHand.getAsJsonArray("enchantments").isEmpty(), "enchantments");
      assertEquals(
          "minecraft:diamond_helmet",
          equipmentItem(mcpEquipment, "HEAD").get("itemId").getAsString(),
          "MCP head equipment");
      assertTrue(
          equipmentSlot(mcpEquipment, "OFF_HAND").get("item").isJsonNull(), "empty off hand");

      JsonObject mcpTarget =
          mcpTool(context, mcp.endpoint(), 10, "minecraft.get_target_block", new JsonObject());
      assertEquals(
          "minecraft:furnace", mcpTarget.get("blockId").getAsString(), "MCP targeted block");
      assertEquals("Furnace", mcpTarget.get("displayName").getAsString(), "block display name");
      assertEquals(
          "north",
          mcpTarget.getAsJsonObject("properties").get("facing").getAsString(),
          "block state property");
      assertTrue(mcpTarget.get("blockEntityPresent").getAsBoolean(), "block entity presence");
      JsonObject furnace = mcpTarget.getAsJsonObject("blockEntity");
      assertEquals("minecraft:furnace", furnace.get("typeId").getAsString(), "block entity type");
      assertEquals(
          "minecraft:iron_ore",
          blockEntityItem(furnace, "input").get("itemId").getAsString(),
          "furnace input item");

      JsonObject entityArguments = new JsonObject();
      entityArguments.addProperty("radius", 16);
      entityArguments.addProperty("limit", 8);
      JsonObject mcpEntities =
          mcpTool(context, mcp.endpoint(), 11, "minecraft.get_nearby_entities", entityArguments);
      assertTrue(
          mcpEntities.getAsJsonArray("entities").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(
                  entity -> entity.get("entityType").getAsString().equals("minecraft:minecart")),
          "MCP nearby entity within 16 blocks");
      JsonObject zombie = entity(mcpEntities, "minecraft:zombie");
      assertEquals("Zombie", zombie.get("displayName").getAsString(), "entity display name");
      assertTrue(zombie.get("living").getAsBoolean(), "living entity marker");
      assertTrue(zombie.get("health").getAsDouble() > 0, "living entity health");
      assertEquals("HOSTILE", zombie.get("classification").getAsString(), "hostile classification");
      assertTrue(zombie.get("distance").getAsDouble() > 0, "entity distance");
      JsonObject minecart = entity(mcpEntities, "minecraft:minecart");
      assertTrue(!minecart.get("living").getAsBoolean(), "non-living entity marker");
      assertTrue(minecart.get("health").isJsonNull(), "non-living health absence");

      JsonObject recipeArguments = new JsonObject();
      recipeArguments.addProperty("itemId", "minecraft:diamond_pickaxe");
      JsonObject mcpRecipe =
          mcpTool(context, mcp.endpoint(), 12, "minecraft.get_recipe", recipeArguments);
      assertTrue(!mcpRecipe.getAsJsonArray("recipes").isEmpty(), "MCP diamond pickaxe recipe");
      assertTrue(
          recipeOccurrences(mcpRecipe, "minecraft:dirt") == 1,
          "MCP recipe sees the datapack override");
      assertTrue(
          itemCount(mcpInventory, "minecraft:dirt") >= 1,
          "MCP inventory has the overridden recipe material");

      JsonObject mcpCraftability =
          mcpTool(context, mcp.endpoint(), 13, "minecraft.can_craft", recipeArguments);
      assertTrue(mcpCraftability.get("craftable").getAsBoolean(), "MCP craftability");
      assertTrue(
          mcpCraftability.getAsJsonArray("recipes").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(recipe -> recipe.get("craftable").getAsBoolean()),
          "MCP identifies a craftable recipe variant");

      JsonObject mcpMissing =
          mcpTool(
              context, mcp.endpoint(), 14, "minecraft.get_missing_ingredients", recipeArguments);
      assertTrue(mcpMissing.get("craftable").getAsBoolean(), "MCP missing ingredient counts");

      JsonObject planArguments = new JsonObject();
      planArguments.addProperty("itemId", "minecraft:crafting_table");
      JsonObject mcpPlan =
          mcpTool(context, mcp.endpoint(), 15, "minecraft.get_crafting_plan", planArguments);
      assertTrue(mcpPlan.get("craftable").getAsBoolean(), "MCP recursive crafting plan");
      assertTrue(mcpPlan.getAsJsonArray("steps").size() >= 2, "MCP recursive plan steps");
      assertTrue(mcpPlan.getAsJsonArray("missingMaterials").isEmpty(), "MCP plan raw shortages");
      assertTrue(mcpPlan.getAsJsonArray("issues").isEmpty(), "MCP plan safety issues");

      JsonObject searchArguments = new JsonObject();
      searchArguments.addProperty("query", "diamond pick");
      searchArguments.addProperty("limit", 10);
      JsonObject mcpSearch =
          mcpTool(context, mcp.endpoint(), 16, "minecraft.search_items", searchArguments);
      assertTrue(
          mcpSearch.getAsJsonArray("items").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(
                  item -> item.get("itemId").getAsString().equals("minecraft:diamond_pickaxe")),
          "MCP item search");

      JsonObject mcpCapabilities =
          mcpTool(context, mcp.endpoint(), 17, "minecraft.get_capabilities", new JsonObject());
      assertEquals(13, mcpCapabilities.getAsJsonArray("tools").size(), "MCP capability tool count");
      assertEquals(
          2, mcpCapabilities.getAsJsonArray("integrations").size(), "MCP integration count");
      assertTrue(
          mcpCapabilities.getAsJsonArray("integrations").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(
                  integration -> integration.get("id").getAsString().equals("gametest-bridge")),
          "MCP external integration identity");
    }

    context.waitFor(
        client ->
            client.level == null
                && client.player == null
                && client.getSingleplayerServer() == null);
    assertEquals(
        "MAIN_MENU",
        invokeSuccessfully(context, tools, "minecraft.get_status", "{}").get("state").getAsString(),
        "return-to-menu status");
    assertEquals(
        "MAIN_MENU",
        mcpTool(context, mcp.endpoint(), 19, "minecraft.get_status", new JsonObject())
            .get("state")
            .getAsString(),
        "MCP return-to-menu status");
    assertToolError(
        mcpToolResult(context, mcp.endpoint(), 20, "minecraft.get_inventory", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "return-to-menu gameplay rejection");
    assertTrue(mcp.running(), "MCP listener survives world close");
  }

  private static void verifyNativeRecipes(ClientGameTestContext context, ToolRegistry tools) {
    JsonObject recipes =
        invokeSuccessfully(
            context, tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:command_block\"}");
    JsonObject nativeRecipe = recipe(recipes, NATIVE_COMMAND_BLOCK_RECIPE);
    JsonObject nativeIngredient =
        nativeRecipe.getAsJsonArray("ingredients").get(0).getAsJsonObject();
    assertEquals(
        2, nativeIngredient.getAsJsonArray("itemIds").size(), "native recipe alternatives");
    assertEquals(1, nativeIngredient.get("count").getAsInt(), "native recipe input count");
    assertEquals(1, recipeOccurrences(recipes, "minecraft:dirt"), "custom recipe dirt option");
    assertEquals(1, recipeOccurrences(recipes, "minecraft:stone"), "custom recipe stone option");

    JsonObject craftability =
        invokeSuccessfully(
            context, tools, "minecraft.can_craft", "{\"itemId\":\"minecraft:command_block\"}");
    assertTrue(craftability.get("craftable").getAsBoolean(), "native recipe craftability");
    JsonObject missing =
        invokeSuccessfully(
            context,
            tools,
            "minecraft.get_missing_ingredients",
            "{\"itemId\":\"minecraft:command_block\"}");
    assertTrue(missing.get("craftable").getAsBoolean(), "native recipe missing ingredients");
    JsonObject plan =
        invokeSuccessfully(
            context,
            tools,
            "minecraft.get_crafting_plan",
            "{\"itemId\":\"minecraft:chain_command_block\"}");
    assertTrue(plan.get("craftable").getAsBoolean(), "native recursive crafting plan");
    assertTrue(
        plan.getAsJsonArray("steps").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .map(step -> step.get("recipeId").getAsString())
            .anyMatch(NATIVE_COMMAND_BLOCK_RECIPE::equals),
        "native recursive intermediate recipe");
    assertTrue(
        plan.getAsJsonArray("steps").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .map(step -> step.get("recipeId").getAsString())
            .anyMatch(NATIVE_CHAIN_COMMAND_BLOCK_RECIPE::equals),
        "native recursive final recipe");
    assertTrue(plan.getAsJsonArray("missingMaterials").isEmpty(), "native plan raw shortages");
    assertTrue(plan.getAsJsonArray("issues").isEmpty(), "native plan safety limits");
  }

  private static JsonObject mcpTool(
      ClientGameTestContext context, URI endpoint, long id, String name, JsonObject arguments) {
    JsonObject result = mcpToolResult(context, endpoint, id, name, arguments);
    if (result.get("isError").getAsBoolean()) {
      throw new AssertionError(name + " returned an MCP tool error: " + result);
    }
    return result.getAsJsonObject("structuredContent");
  }

  private static JsonObject mcpToolResult(
      ClientGameTestContext context, URI endpoint, long id, String name, JsonObject arguments) {
    McpResponse response = mcpRequest(context, endpoint, id, "tools/call", name, arguments);
    if (response.status() != 200) {
      throw new AssertionError(name + " MCP call failed: " + response.body());
    }
    return response.body().getAsJsonObject("result");
  }

  private static void assertToolError(
      JsonObject result, String code, String message, boolean retryable, String description) {
    assertTrue(result.get("isError").getAsBoolean(), description + " is an MCP tool error");
    JsonObject error = result.getAsJsonObject("structuredContent");
    assertEquals(code, error.get("code").getAsString(), description + " code");
    assertEquals(message, error.get("message").getAsString(), description + " message");
    assertEquals(retryable, error.get("retryable").getAsBoolean(), description + " retryability");
    assertTrue(error.getAsJsonObject("details").isEmpty(), description + " safe details");
  }

  private static McpResponse mcpRequest(
      ClientGameTestContext context,
      URI endpoint,
      long id,
      String method,
      String name,
      JsonObject arguments) {
    return awaitExternal(
        context,
        () -> {
          JsonObject params = new JsonObject();
          if (name != null) {
            params.addProperty("name", name);
            params.add("arguments", arguments);
          }
          JsonObject requestBody = new JsonObject();
          requestBody.addProperty("jsonrpc", "2.0");
          requestBody.addProperty("id", id);
          requestBody.addProperty("method", method);
          requestBody.add("params", params);
          return mcpPost(endpoint, requestBody, "2026-07-28");
        });
  }

  private static McpResponse mcpInitialize(ClientGameTestContext context, URI endpoint, long id) {
    return awaitExternal(
        context,
        () -> {
          JsonObject params = new JsonObject();
          params.addProperty("protocolVersion", "2026-07-28");
          params.add("capabilities", new JsonObject());
          JsonObject clientInfo = new JsonObject();
          clientInfo.addProperty("name", "Thread packaged game test");
          clientInfo.addProperty("version", "1.0.0");
          params.add("clientInfo", clientInfo);
          JsonObject requestBody = new JsonObject();
          requestBody.addProperty("jsonrpc", "2.0");
          requestBody.addProperty("id", id);
          requestBody.addProperty("method", "initialize");
          requestBody.add("params", params);
          return mcpPost(endpoint, requestBody, null);
        });
  }

  private static McpResponse mcpInitialized(ClientGameTestContext context, URI endpoint) {
    return awaitExternal(
        context,
        () -> {
          JsonObject notification = new JsonObject();
          notification.addProperty("jsonrpc", "2.0");
          notification.addProperty("method", "notifications/initialized");
          return mcpPost(endpoint, notification, "2026-07-28");
        });
  }

  private static McpResponse mcpPost(URI endpoint, JsonObject requestBody, String protocolVersion) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()));
    if (protocolVersion != null) {
      request.header("MCP-Protocol-Version", protocolVersion);
    }
    try {
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
      JsonObject body =
          response.body().isBlank()
              ? null
              : JsonParser.parseString(response.body()).getAsJsonObject();
      return new McpResponse(response.statusCode(), body);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("MCP game-test request was interrupted", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("MCP game-test request failed", exception);
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

  private static ThreadConfig readRuntimeConfig() {
    Path configPath = FabricLoader.getInstance().getConfigDir().resolve("thread.json");
    assertTrue(Files.isRegularFile(configPath), "runtime config file was created before the test");
    try {
      return ThreadConfigLoader.loadOrCreate(configPath);
    } catch (IOException exception) {
      throw new AssertionError("runtime config could not be read", exception);
    }
  }

  private static ThreadConfig expectedConfig() {
    ThreadConfig defaults = ThreadConfig.defaults();
    return new ThreadConfig(
        !Boolean.getBoolean(EXPECT_MCP_DISABLED),
        defaults.mcpBindHost(),
        Integer.getInteger(EXPECTED_MCP_PORT, defaults.mcpPort()),
        defaults.enabledTools(),
        defaults.disabledIntegrations(),
        defaults.maxEntityRadius(),
        defaults.maxEntityResults(),
        defaults.maxItemSearchResults(),
        defaults.maxRequestBytes(),
        defaults.gameThreadTimeoutMillis(),
        defaults.maxConcurrentRequests());
  }

  private static boolean hasInventoryStack(Minecraft client, Item item, int count) {
    if (client.player == null) {
      return false;
    }
    for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
      if (client.player.getInventory().getItem(slot).is(item)
          && client.player.getInventory().getItem(slot).getCount() == count) {
        return true;
      }
    }
    return false;
  }

  private static boolean targetsKnownFurnace(Minecraft client) {
    return client.hitResult instanceof BlockHitResult blockHit
        && blockHit.getBlockPos().equals(new BlockPos(0, 101, 3));
  }

  private static JsonObject equipmentSlot(JsonObject equipment, String slot) {
    return equipment.getAsJsonArray("slots").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("slot").getAsString().equals(slot))
        .findFirst()
        .orElseThrow();
  }

  private static JsonObject equipmentItem(JsonObject equipment, String slot) {
    return equipmentSlot(equipment, slot).getAsJsonObject("item");
  }

  private static JsonObject blockEntityItem(JsonObject blockEntity, String slot) {
    return blockEntity.getAsJsonArray("items").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("slot").getAsString().equals(slot))
        .map(candidate -> candidate.getAsJsonObject("item"))
        .findFirst()
        .orElseThrow();
  }

  private static JsonObject entity(JsonObject result, String entityType) {
    return result.getAsJsonArray("entities").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("entityType").getAsString().equals(entityType))
        .findFirst()
        .orElseThrow();
  }

  private static JsonObject recipe(JsonObject result, String recipeId) {
    return result.getAsJsonArray("recipes").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("recipeId").getAsString().equals(recipeId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing recipe " + recipeId));
  }

  private static int itemCount(JsonObject inventory, String itemId) {
    return inventory.getAsJsonArray("slots").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .map(slot -> slot.getAsJsonObject("stack"))
        .filter(stack -> stack.get("itemId").getAsString().equals(itemId))
        .mapToInt(stack -> stack.get("count").getAsInt())
        .sum();
  }

  private static int recipeOccurrences(JsonObject recipeResult, String itemId) {
    return recipeResult.getAsJsonArray("recipes").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .flatMap(recipe -> recipe.getAsJsonArray("ingredients").asList().stream())
        .map(JsonElement::getAsJsonObject)
        .filter(
            ingredient ->
                ingredient.getAsJsonArray("itemIds").asList().stream()
                    .map(JsonElement::getAsString)
                    .anyMatch(itemId::equals))
        .mapToInt(ingredient -> ingredient.get("count").getAsInt())
        .sum();
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

  private record McpResponse(int status, JsonObject body) {}
}
