package me.clutchy.thread.core.model.game;

import me.clutchy.thread.core.model.validation.ModelValidation;

/**
 * Snapshot of the running Minecraft, loader, and Thread versions.
 *
 * @param minecraftVersion running Minecraft version
 * @param loader canonical loader name
 * @param loaderVersion running loader version
 * @param threadVersion running Thread version
 */
public record GameInfo(
    String minecraftVersion, String loader, String loaderVersion, String threadVersion) {
  public GameInfo {
    minecraftVersion = ModelValidation.nonBlank(minecraftVersion, "minecraftVersion");
    loader = ModelValidation.nonBlank(loader, "loader");
    loaderVersion = ModelValidation.nonBlank(loaderVersion, "loaderVersion");
    threadVersion = ModelValidation.nonBlank(threadVersion, "threadVersion");
  }
}
