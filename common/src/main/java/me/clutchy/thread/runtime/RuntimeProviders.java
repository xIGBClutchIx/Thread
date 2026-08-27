package me.clutchy.thread.runtime;

import java.util.Objects;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.provider.AdvancementProvider;
import me.clutchy.thread.core.provider.ClientOptionsProvider;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.VersionCapabilities;

/** Focused provider implementations supplied by one Minecraft-version adapter. */
public record RuntimeProviders(
    GameProvider game,
    ClientOptionsProvider clientOptions,
    AdvancementProvider advancements,
    PlayerProvider player,
    WorldProvider world,
    RecipeProvider recipes,
    NearbyContainerQuery craftingNearbyBounds,
    VersionCapabilities capabilities) {
  public RuntimeProviders {
    Objects.requireNonNull(game, "game");
    Objects.requireNonNull(clientOptions, "clientOptions");
    Objects.requireNonNull(advancements, "advancements");
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(recipes, "recipes");
    Objects.requireNonNull(craftingNearbyBounds, "craftingNearbyBounds");
    Objects.requireNonNull(capabilities, "capabilities");
  }
}
