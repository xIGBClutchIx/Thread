package me.clutchy.thread.gametest;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Client controls used only by the automated graphical proofs. */
public final class ClientTestClient {
  private static final int RENDER_DISTANCE = 2;
  private static final int SIMULATION_DISTANCE = 5;
  private static final int FRAME_RATE_LIMIT = 30;

  private ClientTestClient() {}

  /**
   * Reduces client-side work before a test world opens and minimizes visible local test windows.
   *
   * <p>GitHub Actions already contains the graphical proofs inside Xvfb. Leaving that invisible
   * window active avoids Minecraft's iconified-window throttling while the integrated server uses
   * the constrained runner to prepare its deterministic test world.
   */
  public static void prepare(Minecraft client) {
    client.options.renderDistance().set(RENDER_DISTANCE);
    client.options.simulationDistance().set(SIMULATION_DISTANCE);
    client.options.framerateLimit().set(FRAME_RATE_LIMIT);
    if (!Boolean.parseBoolean(System.getenv("CI"))) {
      GLFW.glfwIconifyWindow(client.getWindow().handle());
    }
  }
}
