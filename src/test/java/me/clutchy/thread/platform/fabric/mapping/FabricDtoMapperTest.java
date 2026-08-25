package me.clutchy.thread.platform.fabric.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.platform.fabric.testing.MinecraftTestBootstrap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
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
    assertNull(mapped.customName());
    assertNull(mapped.durability());
    assertTrue(mapped.enchantments().isEmpty());
    assertNull(mapped.components());
  }

  @Test
  void mapsCustomNamesDurabilityAndSelectedComponentsWithoutRawData() {
    DataComponentMap components =
        DataComponentMap.builder()
            .addAll(DataComponents.COMMON_ITEM_COMPONENTS)
            .set(DataComponents.MAX_STACK_SIZE, 1)
            .set(DataComponents.MAX_DAMAGE, 1561)
            .build();
    ItemStack stack = new ItemStack(Holder.direct(Items.DIAMOND_PICKAXE, components), 1);
    stack.set(DataComponents.ITEM_NAME, Component.literal("Diamond Pickaxe"));
    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Workhorse"));
    stack.set(DataComponents.REPAIR_COST, 2);
    stack.set(
        DataComponents.LORE, new ItemLore(java.util.List.of(Component.literal("Mining tool"))));
    stack.setDamageValue(61);

    ItemStackInfo mapped = mapper.itemStack(stack);

    assertEquals("Diamond Pickaxe", mapped.displayName());
    assertEquals("Workhorse", mapped.customName());
    assertEquals(61, mapped.durability().damage());
    assertEquals(mapped.durability().maximum() - 61, mapped.durability().remaining());
    assertEquals(2, mapped.components().repairCost());
    assertEquals(java.util.List.of("Mining tool"), mapped.components().lore());
  }

  @Test
  void refusesToInventDataForEmptyStacks() {
    assertThrows(IllegalArgumentException.class, () -> mapper.itemStack(ItemStack.EMPTY));
  }
}
