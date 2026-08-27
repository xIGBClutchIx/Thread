package me.clutchy.thread.platform.minecraft.testing;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Creates detached item fixtures through the Minecraft 1.21.11 component API. */
public final class MinecraftTestItemStacks {
  private MinecraftTestItemStacks() {}

  /** Creates one stack with the supplied complete component set. */
  public static ItemStack create(Item item, int count, DataComponentMap components) {
    ItemStack stack = new ItemStack(item, count);
    stack.applyComponents(components);
    return stack;
  }
}
