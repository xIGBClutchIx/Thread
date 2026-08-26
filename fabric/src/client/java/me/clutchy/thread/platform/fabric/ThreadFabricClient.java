package me.clutchy.thread.platform.fabric;

import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationCatalog;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationEnvironment;
import me.clutchy.thread.runtime.ThreadRuntime;
import me.clutchy.thread.runtime.ThreadRuntimeInfo;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

/** Fabric client entry point for Thread. */
public final class ThreadFabricClient implements ClientModInitializer {
  static final String MOD_ID = "thread";

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
    // Fabric invokes this entrypoint before Minecraft's client task loop is ready. Constructing
    // providers is safe here, but even a read-only dispatch must wait until initialization returns.
    runtime =
        ThreadRuntime.start(
            Minecraft.getInstance(),
            loader.getConfigDir().resolve("thread.json"),
            versions,
            FabricIntegrationCatalog.candidates(loader),
            new FabricIntegrationEnvironment(loader),
            ThreadFabricClient.class.getClassLoader());

    ClientLifecycleEvents.CLIENT_STOPPING.register(ignored -> runtime.close());
  }

  private static String requiredVersion(FabricLoader loader, String modId) {
    Optional<ModContainer> container = loader.getModContainer(modId);
    if (container.isEmpty()) {
      throw new IllegalStateException("Required runtime component is not loaded: " + modId);
    }
    return container.orElseThrow().getMetadata().getVersion().getFriendlyString();
  }

  ThreadRuntime runtime() {
    return Objects.requireNonNull(runtime, "runtime has not been initialized");
  }
}
