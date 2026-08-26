package me.clutchy.thread.gametest.neoforge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.config.ThreadConfigLoader;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.gametest.neoforge.integration.ExternalProofIntegration;
import me.clutchy.thread.platform.neoforge.ThreadNeoForgeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Self-driving packaged-client proof for the NeoForge artifact. */
@Mod(value = NeoForgeProviderClientGameTest.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeProviderClientGameTest {
  static final String MOD_ID = "thread_neoforge_gametest";

  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static final int TIMEOUT_TICKS = 3_600;
  private static final String EXPECT_MCP_DISABLED = "thread.gametest.expectMcpDisabled";
  private static final String EXPECTED_MCP_PORT = "thread.gametest.expectedMcpPort";
  private static final String REPORT = "thread.gametest.report";

  private Stage stage = Stage.WAITING_FOR_MENU;
  private CompletableFuture<Void> proof;
  private int elapsedTicks;

  public NeoForgeProviderClientGameTest(IEventBus ignoredModEventBus) {
    NeoForge.EVENT_BUS.addListener(this::onClientTick);
  }

  private void onClientTick(ClientTickEvent.Post event) {
    Minecraft client = Minecraft.getInstance();
    try {
      if (++elapsedTicks > TIMEOUT_TICKS) {
        throw new AssertionError("packaged client proof timed out in " + stage);
      }
      switch (stage) {
        case WAITING_FOR_MENU -> startFromMenu(client);
        case MENU_PROOF -> finishMenuProof(client);
        case WAITING_FOR_CREATE_SCREEN -> createWorld(client);
        case WAITING_FOR_WORLD -> startWorldProof(client);
        case WORLD_PROOF -> finishWorldProof(client);
        case WAITING_FOR_RETURN_TO_MENU -> finishAfterReturn(client);
        case RETURN_TO_MENU_PROOF -> finishReturnToMenuProof(client);
        case FINISHED -> {
          // The client is stopping; no further work is needed.
        }
        default -> throw new AssertionError("unhandled packaged-client stage: " + stage);
      }
    } catch (Throwable failure) {
      fail(client, failure);
    }
  }

  private void startFromMenu(Minecraft client) {
    ThreadAccess thread = ThreadAccess.instance();
    if (!thread.initialized() || !(client.gui.screen() instanceof TitleScreen)) {
      return;
    }

    verifyConfiguration();
    assertEquals(13, thread.tools().descriptors().size(), "registered vanilla tool count");
    if (Boolean.getBoolean(EXPECT_MCP_DISABLED)) {
      assertTrue(!thread.mcpRunning(), "MCP remains stopped when configured off");
      pass(client, "disabled config and loader startup");
      return;
    }

    assertTrue(thread.mcpRunning(), "MCP listener running");
    proof = CompletableFuture.runAsync(() -> verifyMenuAndMcp(thread));
    stage = Stage.MENU_PROOF;
  }

  private void finishMenuProof(Minecraft client) {
    if (!proof.isDone()) {
      return;
    }
    proof.join();
    CreateWorldScreen.openFresh(client, () -> client.gui.setScreen(new TitleScreen()));
    stage = Stage.WAITING_FOR_CREATE_SCREEN;
  }

  private void createWorld(Minecraft client) {
    if (!(client.gui.screen() instanceof CreateWorldScreen screen)) {
      return;
    }
    screen.getUiState().setName("Thread NeoForge Packaged Test");
    AbstractButton createButton =
        screen.children().stream()
            .filter(AbstractButton.class::isInstance)
            .map(AbstractButton.class::cast)
            .filter(button -> button.getMessage().getString().equals("Create New World"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("create-world button was not found"));
    createButton.onPress(new KeyEvent(257, 0, 0));
    stage = Stage.WAITING_FOR_WORLD;
  }

  private void startWorldProof(Minecraft client) {
    ThreadAccess thread = ThreadAccess.instance();
    if (client.level == null
        || client.player == null
        || client.getSingleplayerServer() == null
        || !thread.integratedServerRunning()) {
      return;
    }
    proof = CompletableFuture.runAsync(() -> verifyWorld(thread));
    stage = Stage.WORLD_PROOF;
  }

  private void finishWorldProof(Minecraft client) {
    if (!proof.isDone()) {
      return;
    }
    proof.join();
    client.disconnectWithSavingScreen();
    stage = Stage.WAITING_FOR_RETURN_TO_MENU;
  }

  private void finishAfterReturn(Minecraft client) {
    if (client.level != null || client.player != null || client.getSingleplayerServer() != null) {
      return;
    }
    ThreadAccess thread = ThreadAccess.instance();
    proof = CompletableFuture.runAsync(() -> verifyReturnToMenu(thread));
    stage = Stage.RETURN_TO_MENU_PROOF;
  }

  private void finishReturnToMenuProof(Minecraft client) {
    if (!proof.isDone()) {
      return;
    }
    proof.join();
    pass(client, "menu-world-menu, MCP, recipes, tools, and integration discovery");
  }

  private static void verifyReturnToMenu(ThreadAccess thread) {
    JsonObject status = invoke(thread.tools(), "minecraft.get_status", "{}");
    assertEquals("MAIN_MENU", status.get("state").getAsString(), "return-to-menu status");
    assertToolError(
        mcpToolResult(
            thread.mcpServer().endpoint(), 30, "minecraft.get_inventory", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "menu gameplay rejection");
    assertTrue(thread.mcpRunning(), "MCP listener survives world close");
  }

  private static void verifyMenuAndMcp(ThreadAccess thread) {
    JsonObject status = invoke(thread.tools(), "minecraft.get_status", "{}");
    assertEquals("MAIN_MENU", status.get("state").getAsString(), "menu status");
    JsonObject game = invoke(thread.tools(), "minecraft.get_game_info", "{}");
    assertEquals("neoforge", game.get("loader").getAsString(), "loader identity");
    assertEquals("26.2", game.get("minecraftVersion").getAsString(), "Minecraft version");

    URI endpoint = thread.mcpServer().endpoint();
    assertEquals(expectedConfig().mcpPort(), endpoint.getPort(), "configured MCP port");
    JsonObject initialize = mcpInitialize(endpoint, 0).body();
    assertEquals(
        "2026-07-28",
        initialize.getAsJsonObject("result").get("protocolVersion").getAsString(),
        "MCP initialize protocol");
    assertEquals(202, mcpInitialized(endpoint).status(), "initialized notification status");
    JsonObject discovery = mcpRequest(endpoint, 1, "server/discover", null, null).body();
    assertEquals(
        "2026-07-28",
        discovery
            .getAsJsonObject("result")
            .getAsJsonArray("supportedVersions")
            .get(0)
            .getAsString(),
        "MCP discovery protocol");
    JsonObject catalog = mcpRequest(endpoint, 2, "tools/list", null, null).body();
    assertEquals(
        13, catalog.getAsJsonObject("result").getAsJsonArray("tools").size(), "MCP tool catalog");
    assertEquals(
        "MAIN_MENU",
        mcpTool(endpoint, 3, "minecraft.get_status", new JsonObject()).get("state").getAsString(),
        "MCP menu status");
    assertToolError(
        mcpToolResult(endpoint, 4, "minecraft.get_inventory", new JsonObject()),
        "WORLD_NOT_AVAILABLE",
        "initial menu gameplay rejection");
  }

  private static void verifyWorld(ThreadAccess thread) {
    ToolRegistry tools = thread.tools();
    JsonObject status = invoke(tools, "minecraft.get_status", "{}");
    assertEquals("SINGLEPLAYER", status.get("state").getAsString(), "single-player state");
    assertTrue(status.get("supported").getAsBoolean(), "single-player support");
    assertEquals(
        "minecraft:overworld",
        invoke(tools, "minecraft.get_player", "{}").get("dimension").getAsString(),
        "player dimension");
    invoke(tools, "minecraft.get_inventory", "{}");
    assertEquals(
        6,
        invoke(tools, "minecraft.get_equipment", "{}").getAsJsonArray("slots").size(),
        "equipment slots");
    invoke(tools, "minecraft.get_nearby_entities", "{\"radius\":16,\"limit\":8}");
    JsonObject search =
        invoke(tools, "minecraft.search_items", "{\"query\":\"diamond pick\",\"limit\":10}");
    assertTrue(
        search.getAsJsonArray("items").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .anyMatch(item -> item.get("itemId").getAsString().equals("minecraft:diamond_pickaxe")),
        "live item registry search");

    JsonObject recipe =
        invoke(tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:command_block\"}");
    assertTrue(!recipe.getAsJsonArray("recipes").isEmpty(), "NeoForge mod recipe present");
    assertEquals(1, recipeOccurrences(recipe, "minecraft:dirt"), "NeoForge mod recipe dirt input");
    assertEquals(
        1, recipeOccurrences(recipe, "minecraft:stone"), "NeoForge mod recipe stone input");
    JsonObject craftability =
        invoke(tools, "minecraft.can_craft", "{\"itemId\":\"minecraft:command_block\"}");
    assertTrue(!craftability.get("craftable").getAsBoolean(), "empty inventory craftability");
    JsonObject missing =
        invoke(
            tools, "minecraft.get_missing_ingredients", "{\"itemId\":\"minecraft:command_block\"}");
    assertTrue(!missing.get("craftable").getAsBoolean(), "missing ingredients reported");
    JsonObject plan =
        invoke(
            tools, "minecraft.get_crafting_plan", "{\"itemId\":\"minecraft:chain_command_block\"}");
    assertTrue(!plan.getAsJsonArray("missingMaterials").isEmpty(), "crafting-plan shortages");
    assertTrue(ExternalProofIntegration.recipeLookups() > 0, "external recipe contribution used");

    JsonObject capabilities = invoke(tools, "minecraft.get_capabilities", "{}");
    assertTrue(
        capabilities.getAsJsonArray("integrations").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .anyMatch(value -> value.get("id").getAsString().equals("gametest-bridge")),
        "external integration discovery");

    URI endpoint = thread.mcpServer().endpoint();
    assertEquals(
        "SINGLEPLAYER",
        mcpTool(endpoint, 20, "minecraft.get_status", new JsonObject()).get("state").getAsString(),
        "MCP world status");
    mcpTool(endpoint, 21, "minecraft.get_player", new JsonObject());
    JsonObject args = new JsonObject();
    args.addProperty("itemId", "minecraft:command_block");
    assertEquals(
        1,
        recipeOccurrences(mcpTool(endpoint, 22, "minecraft.get_recipe", args), "minecraft:dirt"),
        "MCP NeoForge mod recipe input");
    assertTrue(
        !mcpTool(endpoint, 23, "minecraft.can_craft", args).get("craftable").getAsBoolean(),
        "MCP craftability");
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

  private static void verifyConfiguration() {
    Path path = FMLPaths.CONFIGDIR.get().resolve("thread.json");
    assertTrue(Files.isRegularFile(path), "runtime config was created");
    try {
      assertEquals(expectedConfig(), ThreadConfigLoader.loadOrCreate(path), "runtime config");
    } catch (IOException exception) {
      throw new AssertionError("runtime config could not be read", exception);
    }
  }

  private static JsonObject invoke(ToolRegistry tools, String toolId, String input) {
    ToolResult<JsonElement> result = tools.invoke(toolId, JsonParser.parseString(input));
    if (!result.successful()) {
      throw new AssertionError(toolId + " failed: " + result.error());
    }
    return result.value().getAsJsonObject();
  }

  private static JsonObject mcpTool(URI endpoint, long id, String name, JsonObject arguments) {
    JsonObject result = mcpToolResult(endpoint, id, name, arguments);
    if (result.get("isError").getAsBoolean()) {
      throw new AssertionError(name + " returned an MCP tool error: " + result);
    }
    return result.getAsJsonObject("structuredContent");
  }

  private static JsonObject mcpToolResult(
      URI endpoint, long id, String name, JsonObject arguments) {
    McpResponse response = mcpRequest(endpoint, id, "tools/call", name, arguments);
    assertEquals(200, response.status(), name + " MCP status");
    return response.body().getAsJsonObject("result");
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
    return mcpPost(endpoint, body, "2026-07-28");
  }

  private static McpResponse mcpInitialize(URI endpoint, long id) {
    JsonObject params = new JsonObject();
    params.addProperty("protocolVersion", "2026-07-28");
    params.add("capabilities", new JsonObject());
    JsonObject clientInfo = new JsonObject();
    clientInfo.addProperty("name", "Thread NeoForge packaged game test");
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
    return mcpPost(endpoint, body, "2026-07-28");
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
      throw new IllegalStateException("MCP request was interrupted", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("MCP request failed", exception);
    }
  }

  private static void assertToolError(JsonObject result, String code, String description) {
    assertTrue(result.get("isError").getAsBoolean(), description + " is an MCP tool error");
    assertEquals(
        code, result.getAsJsonObject("structuredContent").get("code").getAsString(), description);
  }

  private static int recipeOccurrences(JsonObject result, String itemId) {
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

  private void pass(Minecraft client, String details) {
    stage = Stage.FINISHED;
    writeReport("PASSED\n" + details + "\n");
    LOGGER.info("Thread NeoForge packaged-client proof passed: {}", details);
    client.stop();
  }

  private void fail(Minecraft client, Throwable failure) {
    stage = Stage.FINISHED;
    writeReport("FAILED\n" + failure + "\n");
    LOGGER.error("Thread NeoForge packaged-client proof failed", failure);
    client.stop();
  }

  private static void writeReport(String contents) {
    String configuredPath = System.getProperty(REPORT);
    if (configuredPath == null) {
      throw new AssertionError("missing packaged-client report path");
    }
    Path path = Path.of(configuredPath);
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, contents);
    } catch (IOException exception) {
      throw new IllegalStateException("could not write packaged-client report", exception);
    }
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

  private enum Stage {
    WAITING_FOR_MENU,
    MENU_PROOF,
    WAITING_FOR_CREATE_SCREEN,
    WAITING_FOR_WORLD,
    WORLD_PROOF,
    WAITING_FOR_RETURN_TO_MENU,
    RETURN_TO_MENU_PROOF,
    FINISHED
  }

  private record McpResponse(int status, JsonObject body) {}

  private record ThreadAccess(ThreadNeoForgeClient entrypoint) {
    static ThreadAccess instance() {
      return new ThreadAccess((ThreadNeoForgeClient) invokeStatic("instance"));
    }

    boolean initialized() {
      return (boolean) invoke("initialized");
    }

    ToolRegistry tools() {
      return (ToolRegistry) invoke("tools");
    }

    me.clutchy.thread.transport.mcp.McpHttpServer mcpServer() {
      return (me.clutchy.thread.transport.mcp.McpHttpServer) invoke("mcpServer");
    }

    boolean mcpRunning() {
      return (boolean) invoke("mcpRunning");
    }

    boolean integratedServerRunning() {
      return (boolean) invoke("integratedServerRunning");
    }

    private Object invoke(String name) {
      try {
        Method method = ThreadNeoForgeClient.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(entrypoint);
      } catch (IllegalAccessException | NoSuchMethodException exception) {
        throw new IllegalStateException("could not inspect Thread entrypoint", exception);
      } catch (InvocationTargetException exception) {
        throw new IllegalStateException("Thread entrypoint accessor failed", exception.getCause());
      }
    }

    private static Object invokeStatic(String name) {
      try {
        Method method = ThreadNeoForgeClient.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(null);
      } catch (IllegalAccessException | NoSuchMethodException exception) {
        throw new IllegalStateException("could not locate Thread entrypoint", exception);
      } catch (InvocationTargetException exception) {
        throw new IllegalStateException("Thread entrypoint lookup failed", exception.getCause());
      }
    }
  }
}
