package me.clutchy.thread.platform.fabric.game;

import java.util.Objects;
import me.clutchy.thread.core.model.game.GameInfo;
import me.clutchy.thread.core.model.game.SessionStatus;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import net.minecraft.client.Minecraft;

/** Fabric-backed provider for menu-safe session state and runtime version metadata. */
public final class FabricGameProvider implements GameProvider {
  private final Minecraft client;
  private final GameThreadExecutor clientThread;
  private final GameInfo gameInfo;

  public FabricGameProvider(Minecraft client, GameThreadExecutor clientThread, GameInfo gameInfo) {
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
    return FabricSessionStatusResolver.resolve(
        worldLoaded,
        client.player != null,
        client.hasSingleplayerServer(),
        worldLoaded && client.isMultiplayerServer());
  }
}
