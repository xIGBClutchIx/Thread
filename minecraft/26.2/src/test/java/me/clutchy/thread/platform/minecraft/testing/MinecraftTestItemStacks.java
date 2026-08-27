package me.clutchy.thread.platform.minecraft.testing;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Creates detached item fixtures through the Minecraft 26.2 component-holder API. */
public final class MinecraftTestItemStacks {
  private MinecraftTestItemStacks() {}

  /** Creates one stack with the supplied complete component set. */
  public static ItemStack create(Item item, int count, DataComponentMap components) {
    return new ItemStack(Holder.direct(item, components), count);
  }
}
