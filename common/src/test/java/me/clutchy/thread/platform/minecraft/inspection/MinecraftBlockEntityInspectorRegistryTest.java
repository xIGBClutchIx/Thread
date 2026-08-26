package me.clutchy.thread.platform.minecraft.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.platform.minecraft.integration.MinecraftIntegrationExtensionPoints;
import me.clutchy.thread.platform.minecraft.mapping.MinecraftDtoMapper;
import me.clutchy.thread.platform.minecraft.testing.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MinecraftBlockEntityInspectorRegistryTest {
  private final MinecraftDtoMapper mapper = new MinecraftDtoMapper();
  private final IntegrationExtensionRegistry extensions = new IntegrationExtensionRegistry();
  private final MinecraftBlockEntityInspectorRegistry inspectors =
      MinecraftBlockEntityInspectorRegistry.vanilla(mapper, extensions);

  @BeforeAll
  static void bootstrapMinecraftRegistries() {
    MinecraftTestBootstrap.initialize();
  }

  @Test
  void exposesNamedFurnaceSlotsWithoutRawBlockEntityData() {
    FurnaceBlockEntity furnace =
        new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
    ItemStack input =
        new ItemStack(Holder.direct(Items.IRON_ORE, DataComponents.COMMON_ITEM_COMPONENTS), 3);
    input.set(DataComponents.ITEM_NAME, Component.literal("Iron Ore"));
    ItemStack fuel =
        new ItemStack(Holder.direct(Items.COAL, DataComponents.COMMON_ITEM_COMPONENTS), 1);
    fuel.set(DataComponents.ITEM_NAME, Component.literal("Coal"));
    furnace.setItem(0, input);
    furnace.setItem(1, fuel);

    BlockEntityInfo result = inspectors.inspect(furnace);

    assertEquals("minecraft:furnace", result.typeId());
    assertEquals(3, result.inventorySize());
    assertEquals(
        List.of("fuel", "input"), result.items().stream().map(item -> item.slot()).toList());
    assertEquals("furnace", result.state().get("kind"));
  }

  @Test
  void doesNotResolveOrMutateUnopenedLootContainers() {
    ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
    chest.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON);

    BlockEntityInfo result = inspectors.inspect(chest);

    assertTrue(result.items().isEmpty());
    assertEquals("false", result.state().get("contentsResolved"));
    assertEquals("minecraft:chests/simple_dungeon", result.state().get("lootTable"));
    assertEquals(BuiltInLootTables.SIMPLE_DUNGEON, chest.getLootTable());
  }

  @Test
  void laterIntegrationInspectorTakesPriorityOverVanillaFallbacks() {
    extensions.register(
        IntegrationId.of("example"),
        MinecraftIntegrationExtensionPoints.BLOCK_ENTITY_INSPECTOR,
        blockEntity ->
            blockEntity instanceof FurnaceBlockEntity
                ? Optional.of(
                    new BlockEntityInfo("example:machine", 0, List.of(), Map.of("speed", "32")))
                : Optional.empty());
    FurnaceBlockEntity furnace =
        new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());

    BlockEntityInfo result = inspectors.inspect(furnace);

    assertEquals("example:machine", result.typeId());
    assertEquals("32", result.state().get("speed"));
  }

  @Test
  void failedOptionalInspectorFallsBackToVanillaInspection() {
    extensions.register(
        IntegrationId.of("broken"),
        MinecraftIntegrationExtensionPoints.BLOCK_ENTITY_INSPECTOR,
        blockEntity -> {
          throw new NoClassDefFoundError("optional machine API");
        });
    FurnaceBlockEntity furnace =
        new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());

    BlockEntityInfo result = inspectors.inspect(furnace);

    assertEquals("minecraft:furnace", result.typeId());
    assertEquals("furnace", result.state().get("kind"));
  }
}
