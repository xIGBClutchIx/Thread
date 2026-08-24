package me.clutchy.thread.platform.fabric.mapping;

import me.clutchy.thread.core.model.ItemStackInfo;
import me.clutchy.thread.core.model.Position;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Converts Minecraft runtime objects into detached Thread DTOs at the platform boundary. */
public final class FabricDtoMapper {
  /** Converts a non-empty Minecraft item stack. */
  public ItemStackInfo itemStack(ItemStack stack) {
    if (stack.isEmpty()) {
      throw new IllegalArgumentException("cannot map an empty item stack");
    }
    return new ItemStackInfo(
        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
        stack.getCount(),
        stack.getMaxStackSize(),
        stack.getHoverName().getString());
  }

  /** Converts an equipment stack, using null for an empty equipment position. */
  public ItemStackInfo optionalItemStack(ItemStack stack) {
    return stack.isEmpty() ? null : itemStack(stack);
  }

  /** Converts an entity's current continuous position. */
  public Position position(Entity entity) {
    return new Position(entity.getX(), entity.getY(), entity.getZ());
  }
}
