package me.clutchy.thread.platform.minecraft.game;

import java.util.Objects;
import me.clutchy.thread.core.model.game.GameInfo;
import me.clutchy.thread.core.model.game.SessionStatus;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import net.minecraft.client.Minecraft;

/** Loader-neutral Minecraft provider for menu-safe session state and runtime metadata. */
public final class MinecraftGameProvider implements GameProvider {
  private final Minecraft client;
  private final GameThreadExecutor clientThread;
  private final GameInfo gameInfo;

  public MinecraftGameProvider(
      Minecraft client, GameThreadExecutor clientThread, GameInfo gameInfo) {
    this.client = Objects.requireNonNull(client, "client");
    this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
    this.gameInfo = Objects.requireNonNull(gameInfo, "gameInfo");
  }

  @Override
  public SessionStatus sessionStatus() {
    return clientThread.call(this::readSessionStatus);
  }

  @Override
  public GameInfo gameInfo() {
    return gameInfo;
  }

  private SessionStatus readSessionStatus() {
    boolean worldLoaded = client.level != null;
    return MinecraftSessionStatusResolver.resolve(
        worldLoaded,
        client.player != null,
        client.hasSingleplayerServer(),
        MinecraftSessionBinding.isMultiplayer(client, worldLoaded));
  }
}
