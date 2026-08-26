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
  }

  /** Verifies the common single-player catalog, native recipes, crafting, and MCP behavior. */
  public static void verifyWorld(ThreadRuntime runtime, boolean nativeRecipeCraftable) {
    ToolRegistry tools = runtime.tools();
    JsonObject status = invoke(tools, "minecraft.get_status", "{}");
    assertEquals("SINGLEPLAYER", status.get("state").getAsString(), "single-player state");
    assertTrue(status.get("supported").getAsBoolean(), "single-player support");
    JsonObject player = invoke(tools, "minecraft.get_player", "{}");
    assertEquals("minecraft:overworld", player.get("dimension").getAsString(), "player dimension");
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

    JsonObject recipe =
        invoke(tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:command_block\"}");
    assertTrue(!recipe.getAsJsonArray("recipes").isEmpty(), "native mod recipe present");
    assertEquals(1, recipeOccurrences(recipe, "minecraft:dirt"), "native recipe dirt input");
    assertEquals(1, recipeOccurrences(recipe, "minecraft:stone"), "native recipe stone input");
    JsonObject craftability =
        invoke(tools, "minecraft.can_craft", "{\"itemId\":\"minecraft:command_block\"}");
    assertEquals(
        nativeRecipeCraftable,
        craftability.get("craftable").getAsBoolean(),
        "native recipe craftability");
    JsonObject missing =
        invoke(
            tools, "minecraft.get_missing_ingredients", "{\"itemId\":\"minecraft:command_block\"}");
    assertEquals(
        nativeRecipeCraftable,
        missing.get("craftable").getAsBoolean(),
        "native recipe missing ingredients");
    JsonObject plan =
        invoke(
            tools, "minecraft.get_crafting_plan", "{\"itemId\":\"minecraft:chain_command_block\"}");
    assertEquals(nativeRecipeCraftable, plan.get("craftable").getAsBoolean(), "native recipe plan");
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
    mcpTool(endpoint, 21, "minecraft.get_player", new JsonObject());
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
    assertTrue(runtime.mcpRunning(), "MCP listener survives world close");
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
