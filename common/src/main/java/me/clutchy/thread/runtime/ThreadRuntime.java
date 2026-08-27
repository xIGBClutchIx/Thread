package me.clutchy.thread.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.config.ThreadConfigLoader;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationEnvironment;
import me.clutchy.thread.core.integration.IntegrationLoader;
import me.clutchy.thread.core.integration.IntegrationRegistry;
import me.clutchy.thread.core.integration.ReflectiveIntegrationLoader;
import me.clutchy.thread.core.integration.extension.CompositeRecipeProvider;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.integration.vanilla.VanillaIntegration;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.provider.AdvancementProvider;
import me.clutchy.thread.core.provider.ClientOptionsProvider;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.platform.minecraft.advancement.MinecraftAdvancementProvider;
import me.clutchy.thread.platform.minecraft.game.MinecraftGameProvider;
import me.clutchy.thread.platform.minecraft.game.MinecraftProviderLimits;
import me.clutchy.thread.platform.minecraft.game.MinecraftSessionGuard;
import me.clutchy.thread.platform.minecraft.inspection.MinecraftBlockEnricherRegistry;
import me.clutchy.thread.platform.minecraft.inspection.MinecraftBlockEntityInspectorRegistry;
import me.clutchy.thread.platform.minecraft.mapping.MinecraftDtoMapper;
import me.clutchy.thread.platform.minecraft.options.MinecraftClientOptionsProvider;
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
  private static final System.Logger LOGGER = System.getLogger(ThreadRuntime.class.getName());

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
   * Starts one client runtime from loader-resolved metadata and paths.
   *
   * <p>The loader edge remains responsible only for resolving its APIs. Configuration fallback,
   * shared assembly, optional integration loading, MCP startup, and startup logging are identical
   * on every loader and therefore live here.
   */
  public static ThreadRuntime start(
      Minecraft client,
      Path configPath,
      ThreadRuntimeInfo info,
      List<IntegrationCandidate> candidates,
      IntegrationEnvironment integrationEnvironment,
      ClassLoader integrationClassLoader) {
    Objects.requireNonNull(configPath, "configPath");
    Objects.requireNonNull(integrationClassLoader, "integrationClassLoader");
    ThreadConfig config = loadConfig(configPath);
    ThreadRuntime runtime =
        create(
            client,
            config,
            info,
            candidates,
            integrationEnvironment,
            new ReflectiveIntegrationLoader(integrationClassLoader));

    if (config.mcpEnabled()) {
      try {
        LOGGER.log(
            System.Logger.Level.INFO,
            "Thread MCP listener started at {0}",
            runtime.startMcp().endpoint());
      } catch (IOException exception) {
        // A local port conflict must not take down Minecraft. Do not include request or game state.
        LOGGER.log(
            System.Logger.Level.ERROR,
            "Thread MCP listener could not bind to {0}:{1} ({2})",
            config.mcpBindHost(),
            config.mcpPort(),
            exception.getClass().getSimpleName());
      }
    }

    LOGGER.log(System.Logger.Level.INFO, info.startupMessage());
    LOGGER.log(
        System.Logger.Level.DEBUG,
        "Thread configuration loaded (MCP enabled: {0})",
        config.mcpEnabled());
    LOGGER.log(
        System.Logger.Level.DEBUG,
        "Thread live providers and {0} vanilla tools initialized",
        runtime.tools().descriptors().size());
    LOGGER.log(
        System.Logger.Level.DEBUG,
        "Thread integrations initialized ({0} active, {1} optional candidates)",
        runtime.integrations().integrations().size(),
        runtime.optionalIntegrationCount());
    return runtime;
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
    ClientOptionsProvider clientOptionsProvider =
        new MinecraftClientOptionsProvider(client, clientThread);
    AdvancementProvider advancementProvider =
        new MinecraftAdvancementProvider(client, clientThread, sessionGuard, gameThreadTimeout);
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
            client,
            clientThread,
            sessionGuard,
            limits,
            mapper,
            entityEnrichers,
            blockEntityInspectors,
            blockEnrichers,
            gameThreadTimeout);

    ToolRegistry toolRegistry = new ToolRegistry();
    IntegrationRegistry integrationRegistry =
        new IntegrationRegistry(toolRegistry, new ContextRegistry(), extensionRegistry);
    integrationRegistry.register(
        new VanillaIntegration(
            gameProvider,
            clientOptionsProvider,
            advancementProvider,
            playerProvider,
            worldProvider,
            recipeProvider,
            new NearbyContainerQuery(limits.maxContainerRadius(), limits.maxContainerResults()),
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
  public synchronized McpHttpServer startMcp() throws IOException {
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
  public synchronized void close() {
    boolean listenerWasRunning = mcpRunning();
    if (mcpServer != null) {
      mcpServer.close();
      mcpServer = null;
    }
    if (listenerWasRunning) {
      LOGGER.log(System.Logger.Level.INFO, "Thread MCP listener stopped");
    }
  }

  private static ThreadConfig loadConfig(Path configPath) {
    try {
      return ThreadConfigLoader.loadOrCreate(configPath);
    } catch (IOException | IllegalArgumentException exception) {
      // Keep Minecraft usable and preserve invalid player input for correction.
      LOGGER.log(
          System.Logger.Level.ERROR,
          "Thread configuration could not be loaded; safe defaults will be used ({0})",
          exception.getClass().getSimpleName());
      return ThreadConfig.defaults();
    }
  }
}
