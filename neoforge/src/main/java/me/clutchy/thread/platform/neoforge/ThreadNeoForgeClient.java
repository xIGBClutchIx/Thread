package me.clutchy.thread.platform.neoforge;

import java.util.Objects;
import me.clutchy.thread.minecraft.v26_2.Minecraft262Runtime;
import me.clutchy.thread.platform.neoforge.integration.NeoForgeIntegrationCatalog;
import me.clutchy.thread.platform.neoforge.integration.NeoForgeIntegrationEnvironment;
import me.clutchy.thread.runtime.ThreadRuntime;
import me.clutchy.thread.runtime.ThreadRuntimeInfo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** NeoForge client entry point for Thread. */
@Mod(value = ThreadNeoForgeClient.MOD_ID, dist = Dist.CLIENT)
public final class ThreadNeoForgeClient {
  static final String MOD_ID = "thread";

  private static ThreadNeoForgeClient instance;

  private ThreadRuntime runtime;

  public ThreadNeoForgeClient(IEventBus modEventBus) {
    instance = this;
    modEventBus.addListener(this::onClientSetup);
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
    ClassLoader modClassLoader = ThreadNeoForgeClient.class.getClassLoader();
    runtime =
        Minecraft262Runtime.start(
            FMLPaths.CONFIGDIR.get().resolve("thread.json"),
            versions,
            NeoForgeIntegrationCatalog.candidates(modClassLoader),
            new NeoForgeIntegrationEnvironment(modList),
            modClassLoader);
  }

  private void onGameShuttingDown(GameShuttingDownEvent event) {
    stopRuntime();
  }

  private void stopRuntime() {
    if (runtime != null) {
      runtime.close();
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

  static ThreadNeoForgeClient instance() {
    return Objects.requireNonNull(instance, "NeoForge entrypoint has not been constructed");
  }

  boolean initialized() {
    return runtime != null;
  }

  ThreadRuntime runtime() {
    return Objects.requireNonNull(runtime, "runtime has not been initialized");
  }
}
