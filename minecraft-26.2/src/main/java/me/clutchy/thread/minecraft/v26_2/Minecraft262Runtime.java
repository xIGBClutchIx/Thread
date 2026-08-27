package me.clutchy.thread.minecraft.v26_2;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationEnvironment;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.provider.GameThreadExecutor;
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
import me.clutchy.thread.runtime.RuntimeProviders;
import me.clutchy.thread.runtime.ThreadRuntime;
import me.clutchy.thread.runtime.ThreadRuntimeInfo;
import net.minecraft.client.Minecraft;

/** Loader-neutral assembly for the Minecraft 26.2 implementation of Thread providers. */
public final class Minecraft262Runtime {
  private Minecraft262Runtime() {}

  /** Starts Thread with the 26.2 provider implementation and loader-resolved runtime inputs. */
  public static ThreadRuntime start(
      Path configPath,
      ThreadRuntimeInfo info,
      List<IntegrationCandidate> candidates,
      IntegrationEnvironment integrationEnvironment,
      ClassLoader integrationClassLoader) {
    return ThreadRuntime.start(
        configPath,
        info,
        candidates,
        integrationEnvironment,
        integrationClassLoader,
        Minecraft262Runtime::createProviders);
  }

  private static RuntimeProviders createProviders(
      ThreadConfig config, ThreadRuntimeInfo info, IntegrationExtensionRegistry extensionRegistry) {
    Minecraft client = Minecraft.getInstance();
    Duration gameThreadTimeout = Duration.ofMillis(config.gameThreadTimeoutMillis());
    GameThreadExecutor clientThread = MinecraftThreadExecutor.forClient(client, gameThreadTimeout);
    MinecraftSessionGuard sessionGuard = new MinecraftSessionGuard();
    MinecraftProviderLimits limits =
        MinecraftProviderLimits.configured(
            config.maxEntityRadius(), config.maxEntityResults(), config.maxItemSearchResults());
    MinecraftDtoMapper mapper = new MinecraftDtoMapper();
    MinecraftBlockEntityInspectorRegistry blockEntityInspectors =
        MinecraftBlockEntityInspectorRegistry.vanilla(mapper, extensionRegistry);
    MinecraftBlockEnricherRegistry blockEnrichers =
        new MinecraftBlockEnricherRegistry(extensionRegistry);
    MinecraftEntityEnricherRegistry entityEnrichers =
        new MinecraftEntityEnricherRegistry(extensionRegistry);

    return new RuntimeProviders(
        new MinecraftGameProvider(client, clientThread, info.gameInfo()),
        new MinecraftClientOptionsProvider(client, clientThread),
        new MinecraftAdvancementProvider(client, clientThread, sessionGuard, gameThreadTimeout),
        new MinecraftPlayerProvider(
            client,
            clientThread,
            sessionGuard,
            mapper,
            blockEntityInspectors,
            blockEnrichers,
            gameThreadTimeout),
        new MinecraftWorldProvider(
            client,
            clientThread,
            sessionGuard,
            limits,
            mapper,
            entityEnrichers,
            blockEntityInspectors,
            blockEnrichers,
            gameThreadTimeout),
        new MinecraftRecipeProvider(
            client, clientThread, sessionGuard, limits, mapper, gameThreadTimeout),
        new NearbyContainerQuery(limits.maxContainerRadius(), limits.maxContainerResults()),
        Minecraft262Capabilities.capabilities());
  }
}
