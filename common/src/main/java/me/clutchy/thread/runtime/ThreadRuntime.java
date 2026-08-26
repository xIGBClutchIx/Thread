package me.clutchy.thread.runtime;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationEnvironment;
import me.clutchy.thread.core.integration.IntegrationLoader;
import me.clutchy.thread.core.integration.IntegrationRegistry;
import me.clutchy.thread.core.integration.extension.CompositeRecipeProvider;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.integration.vanilla.VanillaIntegration;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.platform.minecraft.game.MinecraftGameProvider;
import me.clutchy.thread.platform.minecraft.game.MinecraftProviderLimits;
import me.clutchy.thread.platform.minecraft.game.MinecraftSessionGuard;
import me.clutchy.thread.platform.minecraft.inspection.MinecraftBlockEnricherRegistry;
import me.clutchy.thread.platform.minecraft.inspection.MinecraftBlockEntityInspectorRegistry;
import me.clutchy.thread.platform.minecraft.mapping.MinecraftDtoMapper;
import me.clutchy.thread.platform.minecraft.player.MinecraftPlayerProvider;
import me.clutchy.thread.platform.minecraft.recipe.MinecraftRecipeProvider;
import me.clutchy.thread.platform.minecraft.threading.MinecraftThreadExecutor;
import me.clutchy.thread.platform.minecraft.world.MinecraftEntityEnricherRegistry;
import me.clutchy.thread.platform.minecraft.world.MinecraftWorldProvider;
import me.clutchy.thread.transport.mcp.McpHttpServer;
import me.clutchy.thread.transport.mcp.McpServerOptions;
import net.minecraft.client.Minecraft;

/** Shared assembly and lifecycle for one loader-provided Thread client runtime. */
public final class ThreadRuntime implements AutoCloseable {
  private final ThreadConfig config;
  private final ThreadRuntimeInfo info;
  private final ToolRegistry tools;
  private final IntegrationRegistry integrations;
  private final int optionalIntegrationCount;

  private McpHttpServer mcpServer;

  private ThreadRuntime(
      ThreadConfig config,
      ThreadRuntimeInfo info,
      ToolRegistry tools,
      IntegrationRegistry integrations,
      int optionalIntegrationCount) {
    this.config = config;
    this.info = info;
    this.tools = tools;
    this.integrations = integrations;
    this.optionalIntegrationCount = optionalIntegrationCount;
  }

  /**
   * Creates the complete shared runtime after a loader has supplied its metadata and integration
   * candidates.
   */
  public static ThreadRuntime create(
      Minecraft client,
      ThreadConfig config,
      ThreadRuntimeInfo info,
      List<IntegrationCandidate> candidates,
      IntegrationEnvironment integrationEnvironment,
      IntegrationLoader integrationLoader) {
    Objects.requireNonNull(client, "client");
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(info, "info");
    candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    Objects.requireNonNull(integrationEnvironment, "integrationEnvironment");
    Objects.requireNonNull(integrationLoader, "integrationLoader");

    Duration gameThreadTimeout = Duration.ofMillis(config.gameThreadTimeoutMillis());
    GameThreadExecutor clientThread = MinecraftThreadExecutor.forClient(client, gameThreadTimeout);
    MinecraftSessionGuard sessionGuard = new MinecraftSessionGuard();
    MinecraftProviderLimits limits =
        MinecraftProviderLimits.configured(
            config.maxEntityRadius(), config.maxEntityResults(), config.maxItemSearchResults());
    MinecraftDtoMapper mapper = new MinecraftDtoMapper();
    IntegrationExtensionRegistry extensionRegistry = new IntegrationExtensionRegistry();
    MinecraftBlockEntityInspectorRegistry blockEntityInspectors =
        MinecraftBlockEntityInspectorRegistry.vanilla(mapper, extensionRegistry);
    MinecraftBlockEnricherRegistry blockEnrichers =
        new MinecraftBlockEnricherRegistry(extensionRegistry);
    MinecraftEntityEnricherRegistry entityEnrichers =
        new MinecraftEntityEnricherRegistry(extensionRegistry);
    RecipeProvider recipeProvider =
        new CompositeRecipeProvider(
            new MinecraftRecipeProvider(
                client, clientThread, sessionGuard, limits, mapper, gameThreadTimeout),
            extensionRegistry);

    GameProvider gameProvider = new MinecraftGameProvider(client, clientThread, info.gameInfo());
    PlayerProvider playerProvider =
        new MinecraftPlayerProvider(
            client,
            clientThread,
            sessionGuard,
            mapper,
            blockEntityInspectors,
            blockEnrichers,
            gameThreadTimeout);
    WorldProvider worldProvider =
        new MinecraftWorldProvider(
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
            () -> integrationRegistry.capabilities(info.threadVersion())));
    int activatedCandidates =
        integrationRegistry
            .discover(
                candidates, integrationEnvironment, config::integrationEnabled, integrationLoader)
            .size();

    return new ThreadRuntime(config, info, toolRegistry, integrationRegistry, activatedCandidates);
  }

  /** Starts the loopback MCP listener using the validated shared configuration. */
  public McpHttpServer startMcp() throws IOException {
    if (!config.mcpEnabled()) {
      throw new IllegalStateException("MCP is disabled by configuration");
    }
    if (mcpServer != null && mcpServer.running()) {
      return mcpServer;
    }
    McpServerOptions options =
        McpServerOptions.configured(
            config.mcpBindHost(),
            config.mcpPort(),
            config.maxRequestBytes(),
            config.maxConcurrentRequests(),
            info.threadVersion());
    mcpServer = McpHttpServer.start(tools, options);
    return mcpServer;
  }

  /** Returns the final tool registry after all integration candidates have been processed. */
  public ToolRegistry tools() {
    return tools;
  }

  /** Returns the final active integration registry. */
  public IntegrationRegistry integrations() {
    return integrations;
  }

  /** Returns how many optional candidates reached an activation result. */
  public int optionalIntegrationCount() {
    return optionalIntegrationCount;
  }

  /** Returns the running MCP server. */
  public McpHttpServer mcpServer() {
    return Objects.requireNonNull(mcpServer, "MCP server is not running");
  }

  /** Reports whether this runtime currently owns a live MCP listener. */
  public boolean mcpRunning() {
    return mcpServer != null && mcpServer.running();
  }

  /** Stops the listener without coupling the shared runtime to any loader lifecycle API. */
  @Override
  public void close() {
    if (mcpServer != null) {
      mcpServer.close();
    }
  }
}
