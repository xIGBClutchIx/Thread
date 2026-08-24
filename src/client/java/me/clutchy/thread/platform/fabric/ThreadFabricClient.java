package me.clutchy.thread.platform.fabric;

import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.platform.fabric.game.FabricGameProvider;
import me.clutchy.thread.platform.fabric.game.FabricProviderLimits;
import me.clutchy.thread.platform.fabric.game.FabricSessionGuard;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import me.clutchy.thread.platform.fabric.player.FabricPlayerProvider;
import me.clutchy.thread.platform.fabric.recipe.FabricRecipeProvider;
import me.clutchy.thread.platform.fabric.threading.MinecraftThreadExecutor;
import me.clutchy.thread.platform.fabric.world.FabricWorldProvider;
import net.fabricmc.api.ClientModInitializer;
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

    LOGGER.info(versions.startupMessage());
    LOGGER.debug(
        "Thread configuration defaults initialized (MCP enabled: {})", config.mcpEnabled());
    // Fabric invokes this entrypoint before Minecraft's client task loop is ready. Constructing
    // providers is safe here, but even a read-only dispatch must wait until initialization returns.
    LOGGER.debug("Thread live providers initialized");
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
}
