package me.clutchy.thread.platform.fabric.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

/** Receives JEI's supported runtime lifecycle without exposing JEI types outside this adapter. */
@JeiPlugin
public final class ThreadJeiPlugin implements IModPlugin {
  private static final Identifier PLUGIN_ID =
      Identifier.fromNamespaceAndPath("thread", "recipe_bridge");

  @Override
  public Identifier getPluginUid() {
    return PLUGIN_ID;
  }

  @Override
  public void onRuntimeAvailable(IJeiRuntime runtime) {
    // JEI supplies its query API only through this callback. Keep the unavoidable lifecycle bridge
    // private to the adapter and clear it as soon as JEI invalidates the runtime.
    JeiRuntimeBridge.available(runtime);
  }

  @Override
  public void onRuntimeUnavailable() {
    JeiRuntimeBridge.unavailable();
  }
}
