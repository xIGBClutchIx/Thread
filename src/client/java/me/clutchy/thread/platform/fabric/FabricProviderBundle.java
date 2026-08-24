package me.clutchy.thread.platform.fabric;

import java.util.Objects;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;

/** Explicitly wired live providers retained by the Fabric client lifecycle. */
record FabricProviderBundle(
    GameProvider game, PlayerProvider player, WorldProvider world, RecipeProvider recipe) {
  FabricProviderBundle {
    Objects.requireNonNull(game, "game");
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(recipe, "recipe");
  }
}
