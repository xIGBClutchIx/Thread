package me.clutchy.thread.runtime;

import java.util.Objects;
import me.clutchy.thread.core.model.game.GameInfo;

/** Loader-neutral identity for one running Thread client. */
public record ThreadRuntimeInfo(
    String threadVersion,
    String minecraftVersion,
    String loaderId,
    String loaderDisplayName,
    String loaderVersion) {
  public ThreadRuntimeInfo {
    threadVersion = requireText(threadVersion, "threadVersion");
    minecraftVersion = requireText(minecraftVersion, "minecraftVersion");
    loaderId = requireText(loaderId, "loaderId");
    loaderDisplayName = requireText(loaderDisplayName, "loaderDisplayName");
    loaderVersion = requireText(loaderVersion, "loaderVersion");
  }

  /** Returns the stable loader-neutral game information exposed by Thread tools. */
  public GameInfo gameInfo() {
    return new GameInfo(minecraftVersion, loaderId, loaderVersion, threadVersion);
  }

  /** Returns a concise startup message without coupling shared runtime code to a logger. */
  public String startupMessage() {
    return "Thread %s initialized for Minecraft %s with %s %s"
        .formatted(threadVersion, minecraftVersion, loaderDisplayName, loaderVersion);
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
