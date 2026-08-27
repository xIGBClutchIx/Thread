package me.clutchy.thread.platform.forge;

import java.util.List;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

/** Compile-time binding for Forge 65's static loaded-mod list. */
public final class ForgeModListBinding {
  private ForgeModListBinding() {}

  /** Returns the loaded Forge mod metadata. */
  public static List<IModInfo> mods() {
    return ModList.getMods();
  }
}
