package me.clutchy.thread.platform.fabric;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.config.ThreadConfigLoader;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.integration.IntegrationRegistry;
import me.clutchy.thread.core.integration.ReflectiveIntegrationLoader;
import me.clutchy.thread.core.integration.extension.CompositeRecipeProvider;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.integration.vanilla.VanillaIntegration;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.platform.fabric.game.FabricGameProvider;
import me.clutchy.thread.platform.fabric.game.FabricProviderLimits;
import me.clutchy.thread.platform.fabric.game.FabricSessionGuard;
import me.clutchy.thread.platform.fabric.inspection.FabricBlockEnricherRegistry;
import me.clutchy.thread.platform.fabric.inspection.FabricBlockEntityInspectorRegistry;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationCatalog;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationEnvironment;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import me.clutchy.thread.platform.fabric.player.FabricPlayerProvider;
import me.clutchy.thread.platform.fabric.recipe.FabricRecipeProvider;
import me.clutchy.thread.platform.fabric.threading.MinecraftThreadExecutor;
import me.clutchy.thread.platform.fabric.world.FabricEntityEnricherRegistry;
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

  private ToolRegistry tools;
  private IntegrationRegistry integrations;
  private McpHttpServer mcpServer;

  @Override
  public void onInitializeClient() {
    FabricLoader loader = FabricLoader.getInstance();
    ThreadVersionInfo versions =
        new ThreadVersionInfo(
            requiredVersion(loader, MOD_ID),
            requiredVersion(loader, "minecraft"),
            requiredVersion(loader, "fabricloader"));
    ThreadConfig config = loadConfig(loader);
    Duration gameThreadTimeout = Duration.ofMillis(config.gameThreadTimeoutMillis());
    Minecraft client = Minecraft.getInstance();
    GameThreadExecutor clientThread = MinecraftThreadExecutor.forClient(client, gameThreadTimeout);
    FabricSessionGuard sessionGuard = new FabricSessionGuard();
    FabricProviderLimits limits =
        FabricProviderLimits.configured(
            config.maxEntityRadius(), config.maxEntityResults(), config.maxItemSearchResults());
    FabricDtoMapper mapper = new FabricDtoMapper();
    IntegrationExtensionRegistry extensionRegistry = new IntegrationExtensionRegistry();
    FabricBlockEntityInspectorRegistry blockEntityInspectors =
        FabricBlockEntityInspectorRegistry.vanilla(mapper, extensionRegistry);
    FabricBlockEnricherRegistry blockEnrichers = new FabricBlockEnricherRegistry(extensionRegistry);
    FabricEntityEnricherRegistry entityEnrichers =
        new FabricEntityEnricherRegistry(extensionRegistry);
    RecipeProvider recipeProvider =
        new CompositeRecipeProvider(
            new FabricRecipeProvider(
                client, clientThread, sessionGuard, limits, mapper, gameThreadTimeout),
            extensionRegistry);

    GameProvider gameProvider = new FabricGameProvider(client, clientThread, versions.gameInfo());
    PlayerProvider playerProvider =
        new FabricPlayerProvider(
            client,
            clientThread,
            sessionGuard,
            mapper,
            blockEntityInspectors,
            blockEnrichers,
            gameThreadTimeout);
    WorldProvider worldProvider =
        new FabricWorldProvider(
            client, clientThread, sessionGuard, limits, mapper, entityEnrichers);

    ToolRegistry toolRegistry = new ToolRegistry();
    IntegrationRegistry integrationRegistry =
        new IntegrationRegistry(toolRegistry, new ContextRegistry(), extensionRegistry);
    integrationRegistry.register(
        new VanillaIntegration(
            gameProvider,
            playerProvider,
            worldProvider,
            recipeProvider,
            config::toolEnabled,
            () -> integrationRegistry.capabilities(versions.threadVersion())));
    int optionalIntegrationCount =
        integrationRegistry
            .discover(
                FabricIntegrationCatalog.candidates(loader),
                new FabricIntegrationEnvironment(loader),
                config::integrationEnabled,
                new ReflectiveIntegrationLoader(ThreadFabricClient.class.getClassLoader()))
            .size();
    tools = toolRegistry;
    integrations = integrationRegistry;

    ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> stopMcpServer());
    if (config.mcpEnabled()) {
      startMcpServer(config, versions.threadVersion());
    }

    LOGGER.info(versions.startupMessage());
    LOGGER.debug("Thread configuration loaded (MCP enabled: {})", config.mcpEnabled());
    // Fabric invokes this entrypoint before Minecraft's client task loop is ready. Constructing
    // providers is safe here, but even a read-only dispatch must wait until initialization returns.
    LOGGER.debug(
        "Thread live providers and {} vanilla tools initialized", tools.descriptors().size());
    LOGGER.debug(
        "Thread integrations initialized ({} active, {} optional candidates)",
        integrations.integrations().size(),
        optionalIntegrationCount);
  }

  private void startMcpServer(ThreadConfig config, String threadVersion) {
    try {
      McpServerOptions options =
          McpServerOptions.configured(
              config.mcpBindHost(),
              config.mcpPort(),
              config.maxRequestBytes(),
              config.maxConcurrentRequests(),
              threadVersion);
      mcpServer = McpHttpServer.start(tools(), options);
      LOGGER.info("Thread MCP listener started at {}", mcpServer.endpoint());
    } catch (IOException exception) {
      // A local port conflict must not take down Minecraft. Do not include request or game state.
      LOGGER.error(
          "Thread MCP listener could not bind to {}:{} ({})",
          config.mcpBindHost(),
          config.mcpPort(),
          exception.getClass().getSimpleName());
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

  private static ThreadConfig loadConfig(FabricLoader loader) {
    try {
      return ThreadConfigLoader.loadOrCreate(loader.getConfigDir().resolve("thread.json"));
    } catch (IOException | IllegalArgumentException exception) {
      // Keep Minecraft usable and preserve invalid player input for correction.
      LOGGER.error(
          "Thread configuration could not be loaded; safe defaults will be used ({})",
          exception.getClass().getSimpleName());
      return ThreadConfig.defaults();
    }
  }

  ToolRegistry tools() {
    return Objects.requireNonNull(tools, "tools have not been initialized");
  }

  IntegrationRegistry integrations() {
    return Objects.requireNonNull(integrations, "integrations have not been initialized");
  }

  McpHttpServer mcpServer() {
    return Objects.requireNonNull(mcpServer, "MCP server is not running");
  }

  boolean mcpRunning() {
    return mcpServer != null && mcpServer.running();
  }
}
