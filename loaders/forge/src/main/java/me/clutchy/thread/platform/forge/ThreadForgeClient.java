package me.clutchy.thread.platform.forge;

import java.util.Objects;
import me.clutchy.thread.minecraft.MinecraftRuntime;
import me.clutchy.thread.platform.forge.integration.ForgeIntegrationCatalog;
import me.clutchy.thread.platform.forge.integration.ForgeIntegrationEnvironment;
import me.clutchy.thread.runtime.ThreadRuntime;
import me.clutchy.thread.runtime.ThreadRuntimeInfo;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/** Forge client entry point for Thread. */
@Mod(ThreadForgeClient.MOD_ID)
public final class ThreadForgeClient {
  static final String MOD_ID = "thread";

  private static ThreadForgeClient instance;

  private ThreadRuntime runtime;

  public ThreadForgeClient(FMLJavaModLoadingContext context) {
    instance = this;
    FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(this::onClientSetup);
    GameShuttingDownEvent.BUS.addListener(this::onGameShuttingDown);
  }

  private void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(this::initialize);
  }

  private void initialize() {
    ThreadRuntimeInfo versions =
        new ThreadRuntimeInfo(
            requiredVersion(MOD_ID),
            requiredVersion("minecraft"),
            "forge",
            "Forge",
            requiredVersion("forge"));
    ClassLoader modClassLoader = ThreadForgeClient.class.getClassLoader();
    runtime =
        MinecraftRuntime.start(
            FMLPaths.CONFIGDIR.get().resolve("thread.json"),
            versions,
            ForgeIntegrationCatalog.candidates(modClassLoader),
            new ForgeIntegrationEnvironment(),
            modClassLoader);
  }

  private void onGameShuttingDown(GameShuttingDownEvent event) {
    if (runtime != null) {
      runtime.close();
    }
  }

  private static String requiredVersion(String modId) {
    return ModList.getMods().stream()
        .filter(info -> info.getModId().equals(modId))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Required runtime component is not loaded: " + modId))
        .getVersion()
        .toString();
  }

  static ThreadForgeClient instance() {
    return Objects.requireNonNull(instance, "Forge entrypoint has not been constructed");
  }

  boolean initialized() {
    return runtime != null;
  }

  ThreadRuntime runtime() {
    return Objects.requireNonNull(runtime, "runtime has not been initialized");
  }
}
