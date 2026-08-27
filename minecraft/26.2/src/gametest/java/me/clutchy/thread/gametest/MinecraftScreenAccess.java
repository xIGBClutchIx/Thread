package me.clutchy.thread.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Isolates Minecraft 26.2 screen access used only by packaged-client proof harnesses. */
public final class MinecraftScreenAccess {
  private MinecraftScreenAccess() {}

  public static Screen currentScreen(Minecraft client) {
    return client.gui.screen();
  }

  public static void setScreen(Minecraft client, Screen screen) {
    client.gui.setScreen(screen);
  }
}
