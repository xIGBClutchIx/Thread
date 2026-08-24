package me.clutchy.thread.platform.fabric;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.integration.IntegrationRegistry;
import me.clutchy.thread.core.integration.vanilla.VanillaIntegration;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.platform.fabric.game.FabricGameProvider;
import me.clutchy.thread.platform.fabric.game.FabricProviderLimits;
import me.clutchy.thread.platform.fabric.game.FabricSessionGuard;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import me.clutchy.thread.platform.fabric.player.FabricPlayerProvider;
import me.clutchy.thread.platform.fabric.recipe.FabricRecipeProvider;
import me.clutchy.thread.platform.fabric.threading.MinecraftThreadExecutor;
import me.clutchy.thread.platform.fabric.world.FabricWorldProvider;
import me.clutchy.thread.transport.mcp.McpHttpServer;
import me.clutchy.thread.transport.mcp.McpServerOptions;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric client entry point for Thread. */
public final class ThreadFabricClient implements ClientModInitializer {
  static final String MOD_ID = "thread";

  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  private FabricProviderBundle providers;
  private ToolRegistry tools;
  private McpHttpServer mcpServer;

  @Override
  public void onInitializeClient() {
    FabricLoader loader = FabricLoader.getInstance();
    ThreadVersionInfo versions =
        new ThreadVersionInfo(
            requiredVersion(loader, MOD_ID),
            requiredVersion(loader, "minecraft"),
            requiredVersion(loader, "fabricloader"));
    ThreadConfig config = ThreadConfig.defaults();
    Minecraft client = Minecraft.getInstance();
    GameThreadExecutor clientThread = MinecraftThreadExecutor.forClient(client);
    FabricSessionGuard sessionGuard = new FabricSessionGuard();
    FabricProviderLimits limits = FabricProviderLimits.defaults();
    FabricDtoMapper mapper = new FabricDtoMapper();

    providers =
        new FabricProviderBundle(
            new FabricGameProvider(client, clientThread, versions.gameInfo()),
            new FabricPlayerProvider(client, clientThread, sessionGuard, mapper),
            new FabricWorldProvider(client, clientThread, sessionGuard, limits, mapper),
            new FabricRecipeProvider(client, clientThread, sessionGuard, limits, mapper));

    ToolRegistry toolRegistry = new ToolRegistry();
    IntegrationRegistry integrationRegistry =
        new IntegrationRegistry(toolRegistry, new ContextRegistry());
    integrationRegistry.register(
        new VanillaIntegration(
            providers.game(), providers.player(), providers.world(), providers.recipe()));
    tools = toolRegistry;

    ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> stopMcpServer());
    if (config.mcpEnabled()) {
      startMcpServer(versions.threadVersion());
    }

    LOGGER.info(versions.startupMessage());
    LOGGER.debug(
        "Thread configuration defaults initialized (MCP enabled: {})", config.mcpEnabled());
    // Fabric invokes this entrypoint before Minecraft's client task loop is ready. Constructing
    // providers is safe here, but even a read-only dispatch must wait until initialization returns.
    LOGGER.debug(
        "Thread live providers and {} vanilla tools initialized", tools.descriptors().size());
  }

  private void startMcpServer(String threadVersion) {
    try {
      mcpServer = McpHttpServer.start(tools(), McpServerOptions.loopbackDefaults(threadVersion));
      LOGGER.info("Thread MCP listener started at {}", mcpServer.endpoint());
    } catch (IOException exception) {
      // A local port conflict must not take down Minecraft. The error names the endpoint without
      // serializing any game state, and a later configuration slice will make the port selectable.
      LOGGER.error(
          "Thread MCP listener could not bind to 127.0.0.1:{}",
          McpServerOptions.DEFAULT_PORT,
          exception);
    }
  }

  private void stopMcpServer() {
    if (mcpServer != null && mcpServer.running()) {
      mcpServer.close();
      LOGGER.info("Thread MCP listener stopped");
    }
  }

  private static String requiredVersion(FabricLoader loader, String modId) {
    Optional<ModContainer> container = loader.getModContainer(modId);
    if (container.isEmpty()) {
      throw new IllegalStateException("Required runtime component is not loaded: " + modId);
    }
    return container.orElseThrow().getMetadata().getVersion().getFriendlyString();
  }

  FabricProviderBundle providers() {
    return Objects.requireNonNull(providers, "providers have not been initialized");
  }

  ToolRegistry tools() {
    return Objects.requireNonNull(tools, "tools have not been initialized");
  }

  McpHttpServer mcpServer() {
    return Objects.requireNonNull(mcpServer, "MCP server is not running");
  }
}
