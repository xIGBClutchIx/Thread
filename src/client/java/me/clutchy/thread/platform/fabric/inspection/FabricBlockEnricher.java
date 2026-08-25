package me.clutchy.thread.platform.fabric.inspection;

import me.clutchy.thread.core.model.world.BlockInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric-side extension point for enriching one already-bounded target-block snapshot.
 *
 * <p>The returned value must remain read-only and must not expose live Minecraft or optional-mod
 * objects through {@link BlockInfo}.
 */
@FunctionalInterface
public interface FabricBlockEnricher {
  /** Returns a detached replacement snapshot; {@code blockEntity} may be {@code null}. */
  BlockInfo enrich(
      ServerLevel level,
      BlockPos position,
      BlockState state,
      BlockEntity blockEntity,
      BlockInfo current);
}
