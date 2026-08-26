package me.clutchy.thread.gametest.neoforge;

import static me.clutchy.thread.gametest.LoaderParityAssertions.EXPECT_MCP_DISABLED;
import static me.clutchy.thread.gametest.LoaderParityAssertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import me.clutchy.thread.gametest.ExternalProofIntegration;
import me.clutchy.thread.gametest.LoaderParityAssertions;
import me.clutchy.thread.platform.neoforge.ThreadNeoForgeClient;
import me.clutchy.thread.runtime.ThreadRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Self-driving packaged-client proof for the NeoForge artifact. */
@Mod(value = NeoForgeProviderClientGameTest.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeProviderClientGameTest {
  static final String MOD_ID = "thread_neoforge_gametest";

  private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
  private static final int TIMEOUT_TICKS = 3_600;
  private static final String REPORT = "thread.gametest.report";

  private Stage stage = Stage.WAITING_FOR_MENU;
  private CompletableFuture<Void> proof;
  private int elapsedTicks;

  public NeoForgeProviderClientGameTest(IEventBus ignoredModEventBus) {
    NeoForge.EVENT_BUS.addListener(this::onClientTick);
  }

  private void onClientTick(ClientTickEvent.Post event) {
    Minecraft client = Minecraft.getInstance();
    try {
      if (++elapsedTicks > TIMEOUT_TICKS) {
        throw new AssertionError("packaged client proof timed out in " + stage);
      }
      switch (stage) {
        case WAITING_FOR_MENU -> startFromMenu(client);
        case MENU_PROOF -> finishMenuProof(client);
        case WAITING_FOR_CREATE_SCREEN -> createWorld(client);
        case WAITING_FOR_WORLD -> startWorldProof(client);
        case WORLD_PROOF -> finishWorldProof(client);
        case WAITING_FOR_RETURN_TO_MENU -> finishAfterReturn(client);
        case RETURN_TO_MENU_PROOF -> finishReturnToMenuProof(client);
        case FINISHED -> {
          // The client is stopping; no further work is needed.
        }
        default -> throw new AssertionError("unhandled packaged-client stage: " + stage);
      }
    } catch (Throwable failure) {
      fail(client, failure);
    }
  }

  private void startFromMenu(Minecraft client) {
    ThreadAccess thread = ThreadAccess.instance();
    if (!thread.initialized() || !(client.gui.screen() instanceof TitleScreen)) {
      return;
    }

    ThreadRuntime runtime = thread.runtime();
    LoaderParityAssertions.verifyConfiguration(FMLPaths.CONFIGDIR.get().resolve("thread.json"));
    if (Boolean.getBoolean(EXPECT_MCP_DISABLED)) {
      LoaderParityAssertions.verifyDisabledRuntime(runtime, "external-loader-discovery");
      pass(client, "disabled config and loader startup");
      return;
    }

    proof =
        CompletableFuture.runAsync(
            () ->
                LoaderParityAssertions.verifyMenu(
                    runtime, "neoforge", "external-loader-discovery"));
    stage = Stage.MENU_PROOF;
  }

  private void finishMenuProof(Minecraft client) {
    if (!proof.isDone()) {
      return;
    }
    proof.join();
    CreateWorldScreen.openFresh(client, () -> client.gui.setScreen(new TitleScreen()));
    stage = Stage.WAITING_FOR_CREATE_SCREEN;
  }

  private void createWorld(Minecraft client) {
    if (!(client.gui.screen() instanceof CreateWorldScreen screen)) {
      return;
    }
    screen.getUiState().setName("Thread NeoForge Packaged Test");
    AbstractButton createButton =
        screen.children().stream()
            .filter(AbstractButton.class::isInstance)
            .map(AbstractButton.class::cast)
            .filter(button -> button.getMessage().getString().equals("Create New World"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("create-world button was not found"));
    createButton.onPress(new KeyEvent(257, 0, 0));
    stage = Stage.WAITING_FOR_WORLD;
  }

  private void startWorldProof(Minecraft client) {
    ThreadAccess thread = ThreadAccess.instance();
    if (client.level == null || client.player == null || client.getSingleplayerServer() == null) {
      return;
    }
    proof =
        CompletableFuture.runAsync(
            () -> {
              LoaderParityAssertions.verifyWorld(thread.runtime(), false);
              assertTrue(
                  ExternalProofIntegration.recipeLookups() > 0,
                  "external recipe contribution receives live lookups");
            });
    stage = Stage.WORLD_PROOF;
  }

  private void finishWorldProof(Minecraft client) {
    if (!proof.isDone()) {
      return;
    }
    proof.join();
    client.disconnectWithSavingScreen();
    stage = Stage.WAITING_FOR_RETURN_TO_MENU;
  }

  private void finishAfterReturn(Minecraft client) {
    if (client.level != null || client.player != null || client.getSingleplayerServer() != null) {
      return;
    }
    ThreadAccess thread = ThreadAccess.instance();
    proof =
        CompletableFuture.runAsync(
            () -> LoaderParityAssertions.verifyReturnedToMenu(thread.runtime()));
    stage = Stage.RETURN_TO_MENU_PROOF;
  }

  private void finishReturnToMenuProof(Minecraft client) {
    if (!proof.isDone()) {
      return;
    }
    proof.join();
    pass(client, "menu-world-menu, MCP, recipes, tools, and integration discovery");
  }

  private void pass(Minecraft client, String details) {
    stage = Stage.FINISHED;
    writeReport("PASSED\n" + details + "\n");
    LOGGER.info("Thread NeoForge packaged-client proof passed: {}", details);
    client.stop();
  }

  private void fail(Minecraft client, Throwable failure) {
    stage = Stage.FINISHED;
    writeReport("FAILED\n" + failure + "\n");
    LOGGER.error("Thread NeoForge packaged-client proof failed", failure);
    client.stop();
  }

  private static void writeReport(String contents) {
    String configuredPath = System.getProperty(REPORT);
    if (configuredPath == null) {
      throw new AssertionError("missing packaged-client report path");
    }
    Path path = Path.of(configuredPath);
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, contents);
    } catch (IOException exception) {
      throw new IllegalStateException("could not write packaged-client report", exception);
    }
  }

  private enum Stage {
    WAITING_FOR_MENU,
    MENU_PROOF,
    WAITING_FOR_CREATE_SCREEN,
    WAITING_FOR_WORLD,
    WORLD_PROOF,
    WAITING_FOR_RETURN_TO_MENU,
    RETURN_TO_MENU_PROOF,
    FINISHED
  }

  private record ThreadAccess(ThreadNeoForgeClient entrypoint) {
    static ThreadAccess instance() {
      return new ThreadAccess((ThreadNeoForgeClient) invokeStatic("instance"));
    }

    boolean initialized() {
      return (boolean) invoke("initialized");
    }

    ThreadRuntime runtime() {
      return (ThreadRuntime) invoke("runtime");
    }

    private Object invoke(String name) {
      try {
        Method method = ThreadNeoForgeClient.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(entrypoint);
      } catch (IllegalAccessException | NoSuchMethodException exception) {
        throw new IllegalStateException("could not inspect Thread entrypoint", exception);
      } catch (InvocationTargetException exception) {
        throw new IllegalStateException("Thread entrypoint accessor failed", exception.getCause());
      }
    }

    private static Object invokeStatic(String name) {
      try {
        Method method = ThreadNeoForgeClient.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(null);
      } catch (IllegalAccessException | NoSuchMethodException exception) {
        throw new IllegalStateException("could not locate Thread entrypoint", exception);
      } catch (InvocationTargetException exception) {
        throw new IllegalStateException("Thread entrypoint lookup failed", exception.getCause());
      }
    }
  }
}
