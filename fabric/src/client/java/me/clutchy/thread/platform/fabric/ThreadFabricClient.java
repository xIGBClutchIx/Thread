package me.clutchy.thread.platform.fabric;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.config.ThreadConfigLoader;
import me.clutchy.thread.core.integration.IntegrationRegistry;
import me.clutchy.thread.core.integration.ReflectiveIntegrationLoader;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationCatalog;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationEnvironment;
import me.clutchy.thread.runtime.ThreadRuntime;
import me.clutchy.thread.runtime.ThreadRuntimeInfo;
import me.clutchy.thread.transport.mcp.McpHttpServer;
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

  private ThreadRuntime runtime;

  @Override
  public void onInitializeClient() {
    FabricLoader loader = FabricLoader.getInstance();
    ThreadRuntimeInfo versions =
        new ThreadRuntimeInfo(
            requiredVersion(loader, MOD_ID),
            requiredVersion(loader, "minecraft"),
            "fabric",
            "Fabric Loader",
            requiredVersion(loader, "fabricloader"));
    ThreadConfig config = loadConfig(loader);
    runtime =
        ThreadRuntime.create(
            Minecraft.getInstance(),
            config,
            versions,
            FabricIntegrationCatalog.candidates(loader),
            new FabricIntegrationEnvironment(loader),
            new ReflectiveIntegrationLoader(ThreadFabricClient.class.getClassLoader()));

    ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> stopRuntime());
    if (config.mcpEnabled()) {
      startMcpServer(config);
    }

    LOGGER.info(versions.startupMessage());
    LOGGER.debug("Thread configuration loaded (MCP enabled: {})", config.mcpEnabled());
    // Fabric invokes this entrypoint before Minecraft's client task loop is ready. Constructing
    // providers is safe here, but even a read-only dispatch must wait until initialization returns.
    LOGGER.debug(
        "Thread live providers and {} vanilla tools initialized", tools().descriptors().size());
    LOGGER.debug(
        "Thread integrations initialized ({} active, {} optional candidates)",
        integrations().integrations().size(),
        runtime.optionalIntegrationCount());
  }

  private void startMcpServer(ThreadConfig config) {
    try {
      LOGGER.info("Thread MCP listener started at {}", runtime().startMcp().endpoint());
    } catch (IOException exception) {
      // A local port conflict must not take down Minecraft. Do not include request or game state.
      LOGGER.error(
          "Thread MCP listener could not bind to {}:{} ({})",
          config.mcpBindHost(),
          config.mcpPort(),
          exception.getClass().getSimpleName());
    }
  }

  private void stopRuntime() {
    if (runtime != null && runtime.mcpRunning()) {
      runtime.close();
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
    return runtime().tools();
  }

  IntegrationRegistry integrations() {
    return runtime().integrations();
  }

  McpHttpServer mcpServer() {
    return runtime().mcpServer();
  }

  boolean mcpRunning() {
    return runtime != null && runtime.mcpRunning();
  }

  private ThreadRuntime runtime() {
    return Objects.requireNonNull(runtime, "runtime has not been initialized");
  }
}
