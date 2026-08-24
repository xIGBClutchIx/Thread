package me.clutchy.thread.platform.fabric.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.clutchy.thread.core.model.ItemStackInfo;
import me.clutchy.thread.platform.fabric.testing.MinecraftTestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FabricDtoMapperTest {
  private final FabricDtoMapper mapper = new FabricDtoMapper();

  @BeforeAll
  static void bootstrapMinecraftRegistries() {
    MinecraftTestBootstrap.initialize();
  }

  @Test
  void mapsMinecraftStacksToDetachedCanonicalData() {
    ItemStack stack =
        new ItemStack(Holder.direct(Items.DIAMOND, DataComponents.COMMON_ITEM_COMPONENTS), 3);
    stack.set(DataComponents.ITEM_NAME, Component.literal("Diamond"));

    ItemStackInfo mapped = mapper.itemStack(stack);

    assertEquals("minecraft:diamond", mapped.itemId());
    assertEquals(3, mapped.count());
    assertEquals(64, mapped.maxCount());
    assertFalse(mapped.displayName().isBlank());
  }

  @Test
  void refusesToInventDataForEmptyStacks() {
    assertThrows(IllegalArgumentException.class, () -> mapper.itemStack(ItemStack.EMPTY));
  }
}
