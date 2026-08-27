package me.clutchy.thread.gametest;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Window controls used only by the automated graphical client proofs. */
public final class ClientTestWindow {
  private ClientTestWindow() {}

  /** Minimizes the initialized Minecraft window without affecting normal client launches. */
  public static void minimize(Minecraft client) {
    GLFW.glfwIconifyWindow(client.getWindow().handle());
  }
}
