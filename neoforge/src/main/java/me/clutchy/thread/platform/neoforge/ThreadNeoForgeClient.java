package me.clutchy.thread.platform.neoforge;

import java.io.IOException;
import java.util.Objects;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.config.ThreadConfigLoader;
import me.clutchy.thread.core.integration.IntegrationRegistry;
import me.clutchy.thread.core.integration.ReflectiveIntegrationLoader;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.platform.neoforge.integration.NeoForgeIntegrationCatalog;
import me.clutchy.thread.platform.neoforge.integration.NeoForgeIntegrationEnvironment;
import me.clutchy.thread.runtime.ThreadRuntime;
import me.clutchy.thread.runtime.ThreadRuntimeInfo;
import me.clutchy.thread.transport.mcp.McpHttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NeoForge client entry point for Thread. */
@Mod(value = ThreadNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class ThreadNeoForgeClient {
  static final String MOD_ID = "thread";

  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static ThreadNeoForgeClient instance;

  private ThreadRuntime runtime;
  private boolean integratedServerRunning;

  public ThreadNeoForgeClient(IEventBus modEventBus) {
    instance = this;
    modEventBus.addListener(this::onClientSetup);
    NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    NeoForge.EVENT_BUS.addListener(this::onGameShuttingDown);
  }

  private void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(this::initialize);
  }

  private void initialize() {
    ModList modList = ModList.get();
    ThreadRuntimeInfo versions =
        new ThreadRuntimeInfo(
            requiredVersion(modList, MOD_ID),
            requiredVersion(modList, "minecraft"),
            "neoforge",
            "NeoForge",
            requiredVersion(modList, "neoforge"));
    ThreadConfig config = loadConfig();
    ClassLoader modClassLoader = ThreadNeoForgeClient.class.getClassLoader();
    runtime =
        ThreadRuntime.create(
            Minecraft.getInstance(),
            config,
            versions,
            NeoForgeIntegrationCatalog.candidates(modClassLoader),
            new NeoForgeIntegrationEnvironment(modList),
            new ReflectiveIntegrationLoader(modClassLoader));

    if (config.mcpEnabled()) {
      startMcpServer(config);
    }

    LOGGER.info(versions.startupMessage());
    LOGGER.debug("Thread configuration loaded (MCP enabled: {})", config.mcpEnabled());
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
      LOGGER.error(
          "Thread MCP listener could not bind to {}:{} ({})",
          config.mcpBindHost(),
          config.mcpPort(),
          exception.getClass().getSimpleName());
    }
  }

  private void onServerStarted(ServerStartedEvent event) {
    if (event.getServer() instanceof IntegratedServer) {
      integratedServerRunning = true;
      LOGGER.debug("Thread observed the integrated server start");
    }
  }

  private void onServerStopped(ServerStoppedEvent event) {
    if (event.getServer() instanceof IntegratedServer) {
      integratedServerRunning = false;
      LOGGER.debug("Thread observed the integrated server stop");
    }
  }

  private void onGameShuttingDown(GameShuttingDownEvent event) {
    stopRuntime();
  }

  private synchronized void stopRuntime() {
    if (runtime != null && runtime.mcpRunning()) {
      runtime.close();
      LOGGER.info("Thread MCP listener stopped");
    }
  }

  private static String requiredVersion(ModList modList, String modId) {
    return modList.getMods().stream()
        .filter(info -> info.getModId().equals(modId))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Required runtime component is not loaded: " + modId))
        .getVersion()
        .toString();
  }

  private static ThreadConfig loadConfig() {
    try {
      return ThreadConfigLoader.loadOrCreate(FMLPaths.CONFIGDIR.get().resolve("thread.json"));
    } catch (IOException | IllegalArgumentException exception) {
      LOGGER.error(
          "Thread configuration could not be loaded; safe defaults will be used ({})",
          exception.getClass().getSimpleName());
      return ThreadConfig.defaults();
    }
  }

  static ThreadNeoForgeClient instance() {
    return Objects.requireNonNull(instance, "NeoForge entrypoint has not been constructed");
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

  boolean initialized() {
    return runtime != null;
  }

  boolean integratedServerRunning() {
    return integratedServerRunning;
  }

  private ThreadRuntime runtime() {
    return Objects.requireNonNull(runtime, "runtime has not been initialized");
  }
}
