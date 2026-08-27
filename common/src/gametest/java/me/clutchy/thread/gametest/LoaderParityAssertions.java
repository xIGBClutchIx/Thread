package me.clutchy.thread.gametest;

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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.config.ThreadConfigLoader;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.integration.IntegrationInfo;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.runtime.ThreadRuntime;

/** Shared behavioral contract exercised by every loader's packaged-client test. */
public final class LoaderParityAssertions {
  /** System property used by restart runs to select the expected MCP port. */
  public static final String EXPECTED_MCP_PORT = "thread.gametest.expectedMcpPort";

  /** System property used by disabled-listener runs. */
  public static final String EXPECT_MCP_DISABLED = "thread.gametest.expectMcpDisabled";

  private static final String PROTOCOL_VERSION = "2026-07-28";
  private static final Set<String> EXPECTED_TOOLS =
      Set.of(
          "minecraft.can_craft",
          "minecraft.find_item",
          "minecraft.get_advancement",
          "minecraft.get_advancements",
          "minecraft.get_capabilities",
          "minecraft.get_crafting_plan",
          "minecraft.get_equipment",
          "minecraft.get_game_info",
          "minecraft.get_inventory",
          "minecraft.get_missing_ingredients",
          "minecraft.get_nearby_containers",
          "minecraft.get_nearby_entities",
          "minecraft.get_player",
          "minecraft.get_recipe",
          "minecraft.get_status",
          "minecraft.get_target_block",
          "minecraft.get_target_entity",
          "minecraft.get_world_info",
          "minecraft.inspect_container",
          "minecraft.search_items");

  private LoaderParityAssertions() {}

  /** Verifies configuration persistence shared by normal, restart, and disabled loader runs. */
  public static void verifyConfiguration(Path configPath) {
    assertTrue(Files.isRegularFile(configPath), "runtime config was created");
    try {
      assertEquals(
          expectedConfig(), ThreadConfigLoader.loadOrCreate(configPath), "runtime configuration");
    } catch (IOException exception) {
      throw new AssertionError("runtime config could not be read", exception);
    }
  }

  /** Verifies initialization behavior when MCP is disabled. */
  public static void verifyDisabledRuntime(ThreadRuntime runtime, String integrationSource) {
    verifyCatalogAndIntegrations(runtime, integrationSource);
    assertTrue(!runtime.mcpRunning(), "MCP remains stopped when configured off");
  }

  /** Verifies the loader-neutral menu, discovery, and MCP startup contract. */
  public static void verifyMenu(
      ThreadRuntime runtime, String expectedLoaderId, String integrationSource) {
    verifyCatalogAndIntegrations(runtime, integrationSource);
    JsonObject status = invoke(runtime.tools(), "minecraft.get_status", "{}");
    assertEquals("MAIN_MENU", status.get("state").getAsString(), "menu status");
    JsonObject game = invoke(runtime.tools(), "minecraft.get_game_info", "{}");
    assertEquals(expectedLoaderId, game.get("loader").getAsString(), "loader identity");
    assertEquals("26.2", game.get("minecraftVersion").getAsString(), "Minecraft version");
    assertTrue(!game.get("loaderVersion").getAsString().isBlank(), "loader version");

    assertTrue(runtime.mcpRunning(), "MCP listener running");
    URI endpoint = runtime.mcpServer().endpoint();
    assertEquals(expectedConfig().mcpPort(), endpoint.getPort(), "configured MCP port");
    McpResponse initialize = mcpInitialize(endpoint, 0);
    assertEquals(200, initialize.status(), "MCP initialize status");
    assertEquals(
        PROTOCOL_VERSION,
        initialize.body().getAsJsonObject("result").get("protocolVersion").getAsString(),
        "MCP initialize protocol");
    assertEquals(
        "Thread",
        initialize
            .body()
            .getAsJsonObject("result")
            .getAsJsonObject("serverInfo")
            .get("name")
            .getAsString(),
        "MCP server identity");
    McpResponse initialized = mcpInitialized(endpoint);
    assertEquals(202, initialized.status(), "MCP initialized notification");
    assertTrue(initialized.body() == null, "MCP initialized notification response body");

    JsonObject discovery = mcpRequest(endpoint, 1, "server/discover", null, null).body();
    assertEquals(
        PROTOCOL_VERSION,
        discovery
            .getAsJsonObject("result")
            .getAsJsonArray("supportedVersions")
            .get(0)
            .getAsString(),
        "MCP discovery protocol");
    JsonObject catalog = mcpRequest(endpoint, 2, "tools/list", null, null).body();
    Set<String> mcpTools =
        catalog.getAsJsonObject("result").getAsJsonArray("tools").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .map(tool -> tool.get("name").getAsString())
            .collect(Collectors.toUnmodifiableSet());
    assertEquals(EXPECTED_TOOLS, mcpTools, "MCP tool catalog");
    assertEquals(
        "MAIN_MENU",
        mcpTool(endpoint, 3, "minecraft.get_status", new JsonObject()).get("state").getAsString(),
        "MCP menu status");

    McpResponse missing =
        mcpRequest(endpoint, 4, "tools/call", "minecraft.missing", new JsonObject());
    assertEquals(400, missing.status(), "unknown MCP tool status");
    assertEquals(
        -32_602,
        missing.body().getAsJsonObject("error").get("code").getAsInt(),
        "unknown MCP tool error");
    assertTrue(runtime.mcpRunning(), "invalid MCP call leaves listener running");
    assertToolError(
        mcpToolResult(endpoint, 5, "minecraft.get_inventory", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu gameplay rejection");
    JsonObject nearbyArguments = new JsonObject();
    nearbyArguments.addProperty("radius", 8);
    nearbyArguments.addProperty("limit", 8);
    assertToolError(
        mcpToolResult(endpoint, 6, "minecraft.get_nearby_containers", nearbyArguments),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu container search rejection");
    JsonObject inspectionArguments = new JsonObject();
    JsonObject menuPosition = new JsonObject();
    menuPosition.addProperty("x", 0);
    menuPosition.addProperty("y", 64);
    menuPosition.addProperty("z", 0);
    inspectionArguments.add("position", menuPosition);
    assertToolError(
        mcpToolResult(endpoint, 7, "minecraft.inspect_container", inspectionArguments),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu container inspection rejection");
    assertToolError(
        mcpToolResult(endpoint, 8, "minecraft.find_item", findArguments("coal")),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu live item search rejection");
    assertToolError(
        mcpToolResult(endpoint, 12, "minecraft.get_world_info", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu world-info rejection");
    assertToolError(
        mcpToolResult(endpoint, 38, "minecraft.get_target_entity", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu target-entity rejection");
    JsonObject expandedCrafting = new JsonObject();
    expandedCrafting.addProperty("itemId", "minecraft:command_block");
    expandedCrafting.addProperty("scope", "PLAYER_AND_NEARBY");
    assertToolError(
        mcpToolResult(endpoint, 9, "minecraft.can_craft", expandedCrafting),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu expanded crafting rejection");
    assertToolError(
        mcpToolResult(endpoint, 10, "minecraft.get_advancements", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu advancement-list rejection");
    JsonObject advancementArguments = new JsonObject();
    advancementArguments.addProperty("advancementId", "minecraft:story/root");
    assertToolError(
        mcpToolResult(endpoint, 11, "minecraft.get_advancement", advancementArguments),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "menu advancement-detail rejection");
  }

  /** Verifies the common single-player catalog, native recipes, crafting, and MCP behavior. */
  public static void verifyWorld(ThreadRuntime runtime, boolean nativeRecipeCraftable) {
    ToolRegistry tools = runtime.tools();
    JsonObject status = invoke(tools, "minecraft.get_status", "{}");
    assertEquals("SINGLEPLAYER", status.get("state").getAsString(), "single-player state");
    assertTrue(status.get("supported").getAsBoolean(), "single-player support");
    JsonObject player = invoke(tools, "minecraft.get_player", "{}");
    verifyPlayerStatus(player);
    JsonObject worldInfo = invoke(tools, "minecraft.get_world_info", "{}");
    verifyWorldInfo(worldInfo, player);
    verifyOptionalTargetEntity(invokeResult(tools, "minecraft.get_target_entity", "{}"));
    invoke(tools, "minecraft.get_inventory", "{}");
    assertEquals(
        6,
        invoke(tools, "minecraft.get_equipment", "{}").getAsJsonArray("slots").size(),
        "equipment slots");
    ToolResult<JsonElement> target = invokeResult(tools, "minecraft.get_target_block", "{}");
    if (!target.successful()) {
      assertEquals(ToolErrorCode.NOT_FOUND, target.error().code(), "controlled target absence");
    }
    invoke(tools, "minecraft.get_nearby_entities", "{\"radius\":16,\"limit\":8}");
    invoke(tools, "minecraft.get_nearby_containers", "{\"radius\":16,\"limit\":8}");
    JsonObject playerPosition = player.getAsJsonObject("position");
    String inspectionInput =
        "{\"position\":{\"x\":"
            + (int) Math.floor(playerPosition.get("x").getAsDouble())
            + ",\"y\":"
            + (int) Math.floor(playerPosition.get("y").getAsDouble())
            + ",\"z\":"
            + (int) Math.floor(playerPosition.get("z").getAsDouble())
            + "}}";
    ToolResult<JsonElement> nonContainer =
        invokeResult(tools, "minecraft.inspect_container", inspectionInput);
    assertTrue(!nonContainer.successful(), "non-container inspection is rejected");
    assertEquals(ToolErrorCode.NOT_FOUND, nonContainer.error().code(), "non-container error code");
    JsonObject search =
        invoke(tools, "minecraft.search_items", "{\"query\":\"diamond pick\",\"limit\":10}");
    assertTrue(
        search.getAsJsonArray("items").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .anyMatch(item -> item.get("itemId").getAsString().equals("minecraft:diamond_pickaxe")),
        "live item registry search");
    JsonObject absentLiveItem =
        invoke(tools, "minecraft.find_item", findInput("thread:missing_item"));
    assertTrue(
        absentLiveItem.getAsJsonArray("matches").isEmpty(), "live item search no-match result");

    JsonObject advancements = invoke(tools, "minecraft.get_advancements", "{\"limit\":8}");
    verifyAdvancementList(advancements, "ALL");
    JsonObject completedAdvancements =
        invoke(tools, "minecraft.get_advancements", "{\"filter\":\"COMPLETED\",\"limit\":8}");
    verifyAdvancementList(completedAdvancements, "COMPLETED");
    assertTrue(
        completedAdvancements.getAsJsonArray("advancements").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .allMatch(value -> value.get("completed").getAsBoolean()),
        "completed advancement filter");
    JsonObject incompleteAdvancements =
        invoke(tools, "minecraft.get_advancements", "{\"filter\":\"INCOMPLETE\",\"limit\":8}");
    verifyAdvancementList(incompleteAdvancements, "INCOMPLETE");
    assertTrue(
        incompleteAdvancements.getAsJsonArray("advancements").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .noneMatch(value -> value.get("completed").getAsBoolean()),
        "incomplete advancement filter");
    if (!advancements.getAsJsonArray("advancements").isEmpty()) {
      String advancementId =
          advancements
              .getAsJsonArray("advancements")
              .get(0)
              .getAsJsonObject()
              .get("advancementId")
              .getAsString();
      JsonObject exactSearch =
          invoke(
              tools,
              "minecraft.get_advancements",
              "{\"search\":\"" + advancementId + "\",\"limit\":8}");
      verifyAdvancementList(exactSearch, "ALL");
      assertEquals(1, exactSearch.getAsJsonArray("advancements").size(), "exact ID search");
      assertEquals(
          advancementId,
          exactSearch
              .getAsJsonArray("advancements")
              .get(0)
              .getAsJsonObject()
              .get("advancementId")
              .getAsString(),
          "exact advancement match");
      verifyAdvancementDetail(
          invoke(
              tools, "minecraft.get_advancement", "{\"advancementId\":\"" + advancementId + "\"}"),
          advancementId);
    }

    JsonObject recipe =
        invoke(tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:command_block\"}");
    assertTrue(!recipe.getAsJsonArray("recipes").isEmpty(), "native mod recipe present");
    assertEquals(1, recipeOccurrences(recipe, "minecraft:dirt"), "native recipe dirt input");
    assertEquals(1, recipeOccurrences(recipe, "minecraft:stone"), "native recipe stone input");
    JsonObject craftability =
        invoke(tools, "minecraft.can_craft", "{\"itemId\":\"minecraft:command_block\"}");
    assertEquals("PLAYER_ONLY", craftability.get("scope").getAsString(), "default crafting scope");
    assertTrue(
        craftability.getAsJsonObject("sourceStatus").get("complete").getAsBoolean(),
        "default source snapshot completeness");
    assertEquals(
        nativeRecipeCraftable,
        craftability.get("craftable").getAsBoolean(),
        "native recipe craftability");
    JsonObject explicitPlayer =
        invoke(
            tools,
            "minecraft.can_craft",
            "{\"itemId\":\"minecraft:command_block\",\"scope\":\"PLAYER_ONLY\"}");
    assertEquals(craftability, explicitPlayer, "explicit player-only crafting parity");
    JsonObject expandedCraftability =
        invoke(
            tools,
            "minecraft.can_craft",
            "{\"itemId\":\"minecraft:command_block\",\"scope\":\"PLAYER_AND_NEARBY\"}");
    assertEquals(
        "PLAYER_AND_NEARBY", expandedCraftability.get("scope").getAsString(), "expanded scope");
    assertEquals(
        16D,
        expandedCraftability.getAsJsonObject("sourceStatus").get("nearbyRadius").getAsDouble(),
        "expanded crafting radius");
    assertEquals(
        nativeRecipeCraftable,
        expandedCraftability.get("craftable").getAsBoolean(),
        "expanded native recipe craftability");
    JsonObject missing =
        invoke(
            tools, "minecraft.get_missing_ingredients", "{\"itemId\":\"minecraft:command_block\"}");
    assertEquals(
        nativeRecipeCraftable,
        missing.get("craftable").getAsBoolean(),
        "native recipe missing ingredients");
    assertEquals("PLAYER_ONLY", missing.get("scope").getAsString(), "missing default scope");
    JsonObject plan =
        invoke(
            tools, "minecraft.get_crafting_plan", "{\"itemId\":\"minecraft:chain_command_block\"}");
    assertEquals(nativeRecipeCraftable, plan.get("craftable").getAsBoolean(), "native recipe plan");
    assertEquals("PLAYER_ONLY", plan.get("scope").getAsString(), "plan default scope");
    assertEquals(
        nativeRecipeCraftable,
        plan.getAsJsonArray("missingMaterials").isEmpty(),
        "native plan raw shortages");

    JsonObject capabilities = invoke(tools, "minecraft.get_capabilities", "{}");
    assertEquals(
        EXPECTED_TOOLS.size(), capabilities.getAsJsonArray("tools").size(), "capabilities");
    assertTrue(
        capabilities.getAsJsonArray("integrations").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .anyMatch(value -> value.get("id").getAsString().equals("gametest-bridge")),
        "external integration capability");

    URI endpoint = runtime.mcpServer().endpoint();
    assertEquals(
        "SINGLEPLAYER",
        mcpTool(endpoint, 20, "minecraft.get_status", new JsonObject()).get("state").getAsString(),
        "MCP world status");
    verifyPlayerStatus(mcpTool(endpoint, 21, "minecraft.get_player", new JsonObject()));
    JsonObject recipeArguments = new JsonObject();
    recipeArguments.addProperty("itemId", "minecraft:command_block");
    assertEquals(
        1,
        recipeOccurrences(
            mcpTool(endpoint, 22, "minecraft.get_recipe", recipeArguments), "minecraft:dirt"),
        "MCP native recipe input");
    assertEquals(
        nativeRecipeCraftable,
        mcpTool(endpoint, 23, "minecraft.can_craft", recipeArguments)
            .get("craftable")
            .getAsBoolean(),
        "MCP craftability");
    JsonObject expandedRecipeArguments = recipeArguments.deepCopy();
    expandedRecipeArguments.addProperty("scope", "PLAYER_AND_NEARBY");
    assertEquals(
        "PLAYER_AND_NEARBY",
        mcpTool(endpoint, 27, "minecraft.can_craft", expandedRecipeArguments)
            .get("scope")
            .getAsString(),
        "MCP expanded crafting scope");
    JsonObject nearbyContainerArguments = new JsonObject();
    nearbyContainerArguments.addProperty("radius", 16);
    nearbyContainerArguments.addProperty("limit", 8);
    mcpTool(endpoint, 24, "minecraft.get_nearby_containers", nearbyContainerArguments);
    JsonObject mcpInspectionArguments = JsonParser.parseString(inspectionInput).getAsJsonObject();
    assertToolError(
        mcpToolResult(endpoint, 25, "minecraft.inspect_container", mcpInspectionArguments),
        "NOT_FOUND",
        "The requested position is not a supported container.",
        false,
        "MCP non-container rejection");
    assertTrue(
        mcpTool(endpoint, 26, "minecraft.find_item", findArguments("thread:missing_item"))
            .getAsJsonArray("matches")
            .isEmpty(),
        "MCP live item search no-match result");
    JsonObject mcpAdvancements =
        mcpTool(endpoint, 28, "minecraft.get_advancements", new JsonObject());
    verifyAdvancementList(mcpAdvancements, "ALL");
    if (!mcpAdvancements.getAsJsonArray("advancements").isEmpty()) {
      String advancementId =
          mcpAdvancements
              .getAsJsonArray("advancements")
              .get(0)
              .getAsJsonObject()
              .get("advancementId")
              .getAsString();
      JsonObject advancementArguments = new JsonObject();
      advancementArguments.addProperty("advancementId", advancementId);
      verifyAdvancementDetail(
          mcpTool(endpoint, 29, "minecraft.get_advancement", advancementArguments), advancementId);
    }
    verifyWorldInfo(mcpTool(endpoint, 37, "minecraft.get_world_info", new JsonObject()), player);
    verifyOptionalTargetEntity(
        mcpToolResult(endpoint, 38, "minecraft.get_target_entity", new JsonObject()));
  }

  /** Verifies clean world detachment while the loader-owned client remains running. */
  public static void verifyReturnedToMenu(ThreadRuntime runtime) {
    assertEquals(
        "MAIN_MENU",
        invoke(runtime.tools(), "minecraft.get_status", "{}").get("state").getAsString(),
        "return-to-menu status");
    assertEquals(
        "MAIN_MENU",
        mcpTool(runtime.mcpServer().endpoint(), 30, "minecraft.get_status", new JsonObject())
            .get("state")
            .getAsString(),
        "MCP return-to-menu status");
    assertToolError(
        mcpToolResult(
            runtime.mcpServer().endpoint(), 31, "minecraft.get_inventory", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "return-to-menu gameplay rejection");
    JsonObject nearbyArguments = new JsonObject();
    nearbyArguments.addProperty("radius", 8);
    nearbyArguments.addProperty("limit", 8);
    assertToolError(
        mcpToolResult(
            runtime.mcpServer().endpoint(), 32, "minecraft.get_nearby_containers", nearbyArguments),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "return-to-menu container rejection");
    assertToolError(
        mcpToolResult(
            runtime.mcpServer().endpoint(), 33, "minecraft.find_item", findArguments("coal")),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "return-to-menu live item search rejection");
    assertToolError(
        mcpToolResult(
            runtime.mcpServer().endpoint(), 37, "minecraft.get_world_info", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "return-to-menu world-info rejection");
    assertToolError(
        mcpToolResult(
            runtime.mcpServer().endpoint(), 38, "minecraft.get_target_entity", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "return-to-menu target-entity rejection");
    JsonObject expandedCrafting = new JsonObject();
    expandedCrafting.addProperty("itemId", "minecraft:command_block");
    expandedCrafting.addProperty("scope", "PLAYER_AND_NEARBY");
    assertToolError(
        mcpToolResult(runtime.mcpServer().endpoint(), 34, "minecraft.can_craft", expandedCrafting),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "return-to-menu expanded crafting rejection");
    assertToolError(
        mcpToolResult(
            runtime.mcpServer().endpoint(), 35, "minecraft.get_advancements", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "return-to-menu advancement-list rejection");
    JsonObject advancementArguments = new JsonObject();
    advancementArguments.addProperty("advancementId", "minecraft:story/root");
    assertToolError(
        mcpToolResult(
            runtime.mcpServer().endpoint(), 36, "minecraft.get_advancement", advancementArguments),
        "WORLD_NOT_AVAILABLE",
        "No Minecraft world is currently available.",
        true,
        "return-to-menu advancement-detail rejection");
    assertTrue(runtime.mcpRunning(), "MCP listener survives world close");
  }

  private static void verifyAdvancementList(JsonObject result, String expectedFilter) {
    assertEquals(expectedFilter, result.get("filter").getAsString(), "advancement filter");
    int knownCount = result.get("knownCount").getAsInt();
    int scannedCount = result.get("scannedCount").getAsInt();
    int matchedCount = result.get("matchedCount").getAsInt();
    int limit = result.get("limit").getAsInt();
    int returnedCount = result.getAsJsonArray("advancements").size();
    assertTrue(scannedCount <= knownCount, "advancement provider bound");
    assertTrue(returnedCount <= limit, "advancement result bound");
    assertTrue(returnedCount <= matchedCount, "advancement match count");
    assertEquals(
        knownCount > scannedCount,
        result.get("sourceTruncated").getAsBoolean(),
        "advancement source truncation");
    assertEquals(
        knownCount > scannedCount || matchedCount > returnedCount,
        result.get("truncated").getAsBoolean(),
        "advancement result truncation");
    String previousId = null;
    for (JsonElement element : result.getAsJsonArray("advancements")) {
      JsonObject advancement = element.getAsJsonObject();
      String currentId = advancement.get("advancementId").getAsString();
      if (previousId != null) {
        assertTrue(previousId.compareTo(currentId) <= 0, "advancement ID ordering");
      }
      assertAdvancementProgress(advancement);
      previousId = currentId;
    }
  }

  private static void verifyAdvancementDetail(JsonObject result, String expectedId) {
    assertEquals(expectedId, result.get("advancementId").getAsString(), "advancement detail ID");
    assertAdvancementProgress(result);
    int returnedCompleted = 0;
    String previousName = null;
    for (JsonElement element : result.getAsJsonArray("criteria")) {
      JsonObject criterion = element.getAsJsonObject();
      String currentName = criterion.get("name").getAsString();
      if (previousName != null) {
        assertTrue(previousName.compareTo(currentName) <= 0, "advancement criterion ordering");
      }
      if (criterion.get("completed").getAsBoolean()) {
        returnedCompleted++;
        assertTrue(!criterion.get("obtainedAt").isJsonNull(), "completed criterion timestamp");
      } else {
        assertTrue(criterion.get("obtainedAt").isJsonNull(), "incomplete criterion timestamp");
      }
      previousName = currentName;
    }
    assertTrue(
        returnedCompleted <= result.get("completedCriteria").getAsInt(),
        "returned completed criteria");
  }

  private static void assertAdvancementProgress(JsonObject advancement) {
    double percentage = advancement.get("completionPercentage").getAsDouble();
    assertTrue(percentage >= 0 && percentage <= 100, "advancement percentage range");
    assertTrue(
        advancement.get("completedCriteria").getAsInt()
            <= advancement.get("totalCriteria").getAsInt(),
        "advancement criterion counts");
    assertTrue(
        advancement.get("completedRequirements").getAsInt()
            <= advancement.get("totalRequirements").getAsInt(),
        "advancement requirement counts");
    if (advancement.get("completed").getAsBoolean()) {
      assertEquals(100.0, percentage, "completed advancement percentage");
      assertTrue(!advancement.get("completedAt").isJsonNull(), "advancement completion timestamp");
    } else {
      assertTrue(advancement.get("completedAt").isJsonNull(), "incomplete completion timestamp");
    }
  }

  private static void verifyWorldInfo(JsonObject world, JsonObject player) {
    assertEquals("minecraft:overworld", world.get("dimensionId").getAsString(), "world dimension");
    assertTrue(world.get("biomeId").getAsString().contains(":"), "biome registry identity");
    assertTrue(world.has("biomeName"), "biome name absence is explicit");
    JsonObject clientPosition = player.getAsJsonObject("position");
    JsonObject serverPosition = world.getAsJsonObject("playerPosition");
    double positionDeltaSquared =
        Math.pow(clientPosition.get("x").getAsDouble() - serverPosition.get("x").getAsDouble(), 2)
            + Math.pow(
                clientPosition.get("y").getAsDouble() - serverPosition.get("y").getAsDouble(), 2)
            + Math.pow(
                clientPosition.get("z").getAsDouble() - serverPosition.get("z").getAsDouble(), 2);
    assertTrue(positionDeltaSquared <= 1, "server/client player position agreement");
    assertEquals(
        "minecraft:overworld",
        world.get("worldSpawnDimensionId").getAsString(),
        "world spawn dimension");
    assertTrue(!world.get("distanceFromSpawn").isJsonNull(), "same-dimension spawn distance");
    assertTrue(world.get("distanceFromSpawn").getAsDouble() >= 0, "spawn distance range");
    assertTrue(
        Set.of("peaceful", "easy", "normal", "hard")
            .contains(world.get("difficulty").getAsString()),
        "world difficulty");
    assertTrue(world.get("hardcore").isJsonPrimitive(), "hardcore flag");
    long dayTime = world.get("dayTimeTicks").getAsLong();
    assertEquals(Math.floorDiv(dayTime, 24_000), world.get("worldDay").getAsLong(), "world day");
    assertEquals(
        Math.floorMod(dayTime, 24_000), world.get("timeOfDayTicks").getAsInt(), "time within day");
    assertTrue(
        Set.of("DAY", "NIGHT", "FIXED").contains(world.get("daylightState").getAsString()),
        "daylight state");
    assertTrue(
        !world.get("thundering").getAsBoolean() || world.get("raining").getAsBoolean(),
        "thunder implies rain");
    int light = world.get("localLightLevel").getAsInt();
    assertTrue(light >= 0 && light <= 15, "local light range");
    assertTrue(!world.get("moonPhase").getAsString().isBlank(), "moon phase");
    assertTrue(Double.isFinite(world.get("biomeTemperature").getAsDouble()), "biome temperature");
    assertTrue(world.get("biomeHasPrecipitation").isJsonPrimitive(), "biome precipitation flag");
  }

  private static void verifyPlayerStatus(JsonObject player) {
    assertEquals("minecraft:overworld", player.get("dimension").getAsString(), "player dimension");
    assertTrue(
        Set.of("survival", "creative", "adventure", "spectator")
            .contains(player.get("gameMode").getAsString()),
        "player game mode");
    assertTrue(player.get("hardcore").isJsonPrimitive(), "player hardcore flag");
    assertTrue(player.get("health").getAsDouble() >= 0, "player health");
    assertTrue(
        player.get("health").getAsDouble() <= player.get("maxHealth").getAsDouble(),
        "player health bound");
    JsonObject armor = player.getAsJsonObject("armor");
    assertTrue(armor.get("value").getAsInt() >= 0, "player armor value");
    assertTrue(armor.get("toughness").getAsDouble() >= 0, "player armor toughness");
    JsonObject air = player.getAsJsonObject("air");
    assertTrue(air.get("maximum").getAsInt() > 0, "player maximum air");
    assertTrue(player.getAsJsonArray("activeEffects").size() <= 64, "player active-effect bound");
    String previousEffectId = null;
    for (JsonElement element : player.getAsJsonArray("activeEffects")) {
      JsonObject effect = element.getAsJsonObject();
      String effectId = effect.get("effectId").getAsString();
      if (previousEffectId != null) {
        assertTrue(previousEffectId.compareTo(effectId) <= 0, "player active-effect ordering");
      }
      assertTrue(effect.has("durationTicks"), "player effect duration absence is explicit");
      previousEffectId = effectId;
    }
    assertTrue(player.get("activeEffectsTruncated").isJsonPrimitive(), "effect truncation flag");
    JsonObject movement = player.getAsJsonObject("movement");
    for (String field : List.of("sprinting", "swimming", "crouching", "flying", "onGround")) {
      assertTrue(movement.get(field).isJsonPrimitive(), "player movement field " + field);
    }
    assertTrue(movement.get("fallDistance").getAsDouble() >= 0, "player fall distance");
    JsonObject conditions = player.getAsJsonObject("conditions");
    for (String field : List.of("sleeping", "onFire", "freezing", "fullyFrozen")) {
      assertTrue(conditions.get(field).isJsonPrimitive(), "player condition field " + field);
    }
    int selectedSlot = player.get("selectedHotbarSlot").getAsInt();
    assertTrue(selectedSlot >= 0 && selectedSlot <= 8, "selected hotbar slot");
    double attackCooldown = player.get("attackCooldown").getAsDouble();
    assertTrue(attackCooldown >= 0 && attackCooldown <= 1, "attack cooldown range");
    if (!player.get("vehicle").isJsonNull()) {
      JsonObject vehicle = player.getAsJsonObject("vehicle");
      assertTrue(vehicle.get("entityType").getAsString().contains(":"), "vehicle identity");
      assertTrue(!vehicle.get("displayName").getAsString().isBlank(), "vehicle display name");
      assertTrue(vehicle.has("customName"), "vehicle custom-name absence is explicit");
    }
    if (!player.get("respawn").isJsonNull()) {
      JsonObject respawn = player.getAsJsonObject("respawn");
      assertTrue(respawn.get("dimension").getAsString().contains(":"), "respawn dimension");
      assertTrue(respawn.get("position").isJsonObject(), "respawn position");
      assertTrue(respawn.get("forced").isJsonPrimitive(), "respawn forced flag");
    }
  }

  private static void verifyOptionalTargetEntity(ToolResult<JsonElement> result) {
    if (!result.successful()) {
      assertEquals("NOT_FOUND", result.error().code().name(), "no target entity code");
      assertEquals(
          "The player is not currently targeting a valid entity.",
          result.error().message(),
          "no target entity message");
      assertTrue(result.error().retryable(), "no target entity retryability");
      return;
    }
    verifyTargetEntity(result.value().getAsJsonObject());
  }

  private static void verifyOptionalTargetEntity(JsonObject result) {
    if (result.get("isError").getAsBoolean()) {
      assertToolError(
          result,
          "NOT_FOUND",
          "The player is not currently targeting a valid entity.",
          true,
          "no MCP target entity");
      return;
    }
    verifyTargetEntity(result.getAsJsonObject("structuredContent"));
  }

  private static void verifyTargetEntity(JsonObject entity) {
    assertTrue(entity.get("entityType").getAsString().contains(":"), "target entity identity");
    assertTrue(entity.get("distance").getAsDouble() >= 0, "target entity distance");
    assertTrue(entity.get("position").isJsonObject(), "target entity position");
    assertTrue(entity.get("equipment").isJsonArray(), "target entity equipment");
    assertTrue(entity.get("activeEffects").isJsonArray(), "target entity effects");
    assertTrue(entity.get("activeEffectsTruncated").isJsonPrimitive(), "target effect bound");
  }

  /** Invokes a tool and returns its object result, failing on a controlled tool error. */
  public static JsonObject invoke(ToolRegistry tools, String toolId, String input) {
    ToolResult<JsonElement> result = invokeResult(tools, toolId, input);
    if (!result.successful()) {
      throw new AssertionError(toolId + " failed: " + result.error());
    }
    if (!result.value().isJsonObject()) {
      throw new AssertionError(toolId + " returned a non-object result");
    }
    return result.value().getAsJsonObject();
  }

  /** Calls one tool through the real packaged MCP listener. */
  public static JsonObject mcpTool(URI endpoint, long id, String name, JsonObject arguments) {
    JsonObject result = mcpToolResult(endpoint, id, name, arguments);
    if (result.get("isError").getAsBoolean()) {
      throw new AssertionError(name + " returned an MCP tool error: " + result);
    }
    return result.getAsJsonObject("structuredContent");
  }

  /** Calls one tool through MCP and returns the complete MCP tool result. */
  public static JsonObject mcpToolResult(URI endpoint, long id, String name, JsonObject arguments) {
    McpResponse response = mcpRequest(endpoint, id, "tools/call", name, arguments);
    assertEquals(200, response.status(), name + " MCP status");
    return response.body().getAsJsonObject("result");
  }

  private static String findInput(String query) {
    return "{\"query\":\"" + query + "\",\"radius\":16,\"containerLimit\":8,\"itemLimit\":16}";
  }

  private static JsonObject findArguments(String query) {
    return JsonParser.parseString(findInput(query)).getAsJsonObject();
  }

  /** Counts ingredient requirements containing one canonical item ID. */
  public static int recipeOccurrences(JsonObject result, String itemId) {
    return result.getAsJsonArray("recipes").asList().stream()
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

  /** Small assertion helper usable without a unit-test runtime inside a packaged mod. */
  public static void assertTrue(boolean condition, String description) {
    if (!condition) {
      throw new AssertionError(description);
    }
  }

  /** Small equality assertion usable without a unit-test runtime inside a packaged mod. */
  public static void assertEquals(Object expected, Object actual, String description) {
    if (!expected.equals(actual)) {
      throw new AssertionError(description + ": expected " + expected + " but was " + actual);
    }
  }

  private static void verifyCatalogAndIntegrations(
      ThreadRuntime runtime, String integrationSource) {
    Set<String> toolIds =
        runtime.tools().descriptors().stream()
            .map(descriptor -> descriptor.id().value())
            .collect(Collectors.toUnmodifiableSet());
    assertEquals(EXPECTED_TOOLS, toolIds, "registered tool catalog");
    assertEquals(2, runtime.integrations().integrations().size(), "active integration count");
    IntegrationInfo external =
        runtime.integrations().integrations().stream()
            .filter(integration -> integration.id().value().equals("gametest-bridge"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("external integration was not activated"));
    assertEquals(
        integrationSource,
        external.metadata().get("gametest.source"),
        "external integration discovery source");
    IntegrationInfo vanilla =
        runtime.integrations().integrations().stream()
            .filter(integration -> integration.id().value().equals("vanilla"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("vanilla integration was not activated"));
    assertEquals(
        Integer.toString(EXPECTED_TOOLS.size()),
        vanilla.metadata().get("thread.tool_count"),
        "vanilla contribution metadata");
  }

  private static ToolResult<JsonElement> invokeResult(
      ToolRegistry tools, String toolId, String input) {
    return tools.invoke(toolId, JsonParser.parseString(input));
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

  private static McpResponse mcpRequest(
      URI endpoint, long id, String method, String name, JsonObject arguments) {
    JsonObject params = new JsonObject();
    if (name != null) {
      params.addProperty("name", name);
      params.add("arguments", arguments);
    }
    JsonObject body = new JsonObject();
    body.addProperty("jsonrpc", "2.0");
    body.addProperty("id", id);
    body.addProperty("method", method);
    body.add("params", params);
    return mcpPost(endpoint, body, PROTOCOL_VERSION);
  }

  private static McpResponse mcpInitialize(URI endpoint, long id) {
    JsonObject params = new JsonObject();
    params.addProperty("protocolVersion", PROTOCOL_VERSION);
    params.add("capabilities", new JsonObject());
    JsonObject clientInfo = new JsonObject();
    clientInfo.addProperty("name", "Thread loader parity test");
    clientInfo.addProperty("version", "1.0.0");
    params.add("clientInfo", clientInfo);
    JsonObject body = new JsonObject();
    body.addProperty("jsonrpc", "2.0");
    body.addProperty("id", id);
    body.addProperty("method", "initialize");
    body.add("params", params);
    return mcpPost(endpoint, body, null);
  }

  private static McpResponse mcpInitialized(URI endpoint) {
    JsonObject body = new JsonObject();
    body.addProperty("jsonrpc", "2.0");
    body.addProperty("method", "notifications/initialized");
    return mcpPost(endpoint, body, PROTOCOL_VERSION);
  }

  private static McpResponse mcpPost(URI endpoint, JsonObject body, String protocolVersion) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
    if (protocolVersion != null) {
      request.header("MCP-Protocol-Version", protocolVersion);
    }
    try {
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
      JsonObject responseBody =
          response.body().isBlank()
              ? null
              : JsonParser.parseString(response.body()).getAsJsonObject();
      return new McpResponse(response.statusCode(), responseBody);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("MCP parity request was interrupted", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("MCP parity request failed", exception);
    }
  }

  private record McpResponse(int status, JsonObject body) {}
}
