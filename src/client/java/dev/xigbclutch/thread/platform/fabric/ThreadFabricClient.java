package dev.xigbclutch.thread.platform.fabric;

import dev.xigbclutch.thread.config.ThreadConfig;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric client entry point for Thread. */
public final class ThreadFabricClient implements ClientModInitializer {
  static final String MOD_ID = "thread";

  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  @Override
  public void onInitializeClient() {
    FabricLoader loader = FabricLoader.getInstance();
    ThreadVersionInfo versions =
        new ThreadVersionInfo(
            requiredVersion(loader, MOD_ID),
            requiredVersion(loader, "minecraft"),
            requiredVersion(loader, "fabricloader"));
    ThreadConfig config = ThreadConfig.defaults();

    LOGGER.info(versions.startupMessage());
    LOGGER.debug(
        "Thread configuration defaults initialized (MCP enabled: {})", config.mcpEnabled());
  }

  private static String requiredVersion(FabricLoader loader, String modId) {
    Optional<ModContainer> container = loader.getModContainer(modId);
    if (container.isEmpty()) {
      throw new IllegalStateException("Required runtime component is not loaded: " + modId);
    }
    return container.orElseThrow().getMetadata().getVersion().getFriendlyString();
  }
}
