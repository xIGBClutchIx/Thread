package me.clutchy.thread.platform.minecraft.inspection;

import java.util.Optional;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Minecraft-facing extension point for converting one recognized block entity into safe data.
 *
 * <p>Implementations must select bounded, read-only state explicitly. Returning raw NBT, component
 * maps, or live Minecraft objects is not permitted.
 */
@FunctionalInterface
public interface MinecraftBlockEntityInspector {
  /** Returns an inspection when this implementation recognizes the supplied block entity. */
  Optional<BlockEntityInfo> inspect(BlockEntity blockEntity);
}
