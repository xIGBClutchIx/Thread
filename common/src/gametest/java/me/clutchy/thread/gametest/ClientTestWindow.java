package me.clutchy.thread.gametest;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Window controls used only by the automated graphical client proofs. */
public final class ClientTestWindow {
  private ClientTestWindow() {}

  /**
   * Minimizes the initialized Minecraft window without affecting normal client launches.
   *
   * <p>GitHub Actions already runs the graphical proofs inside Xvfb. Leaving that invisible window
   * active avoids Minecraft's iconified-window throttling while an integrated world is starting on
   * a resource-constrained runner.
   */
  public static void minimize(Minecraft client) {
    if (!Boolean.parseBoolean(System.getenv("CI"))) {
      GLFW.glfwIconifyWindow(client.getWindow().handle());
    }
  }
}
