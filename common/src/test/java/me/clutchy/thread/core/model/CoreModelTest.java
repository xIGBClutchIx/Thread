package me.clutchy.thread.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.game.SessionState;
import me.clutchy.thread.core.model.game.SessionStatus;
import me.clutchy.thread.core.model.item.ItemComponentsInfo;
import me.clutchy.thread.core.model.item.ItemDurabilityInfo;
import me.clutchy.thread.core.model.item.ItemEnchantmentInfo;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.player.EquipmentSlotInfo;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySlotInfo;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.recipe.RecipeIngredientInfo;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockEntityItemInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.DaylightState;
import me.clutchy.thread.core.model.world.EntityClassification;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyContainerSummary;
import me.clutchy.thread.core.model.world.Position;
import me.clutchy.thread.core.model.world.WorldInfo;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class CoreModelTest {
  @Test
  void inventorySnapshotsAreDetachedSortedAndRejectDuplicateSlots() {
    ItemStackInfo stone = item("minecraft:stone", "Stone", 32, 64);
    List<InventorySlotInfo> source = new ArrayList<>();
    source.add(new InventorySlotInfo(8, stone));
    source.add(new InventorySlotInfo(1, stone));

    InventorySnapshot snapshot = new InventorySnapshot(1, source);
    source.clear();

    assertEquals(List.of(1, 8), snapshot.slots().stream().map(InventorySlotInfo::slot).toList());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.slots().clear());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new InventorySnapshot(
                0, List.of(new InventorySlotInfo(1, stone), new InventorySlotInfo(1, stone))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new InventorySlotInfo(InventorySnapshot.MAIN_SLOT_COUNT, stone));
  }

  @Test
  void enrichedItemsAreDetachedValidatedAndDeterministicallyOrdered() {
    List<ItemEnchantmentInfo> enchantments = new ArrayList<>();
    enchantments.add(new ItemEnchantmentInfo("minecraft:unbreaking", 3));
    enchantments.add(new ItemEnchantmentInfo("minecraft:efficiency", 5));
    ItemComponentsInfo components =
        new ItemComponentsInfo(null, false, 2, List.of("Mining tool"), null, 0);

    ItemStackInfo item =
        new ItemStackInfo(
            "minecraft:diamond_pickaxe",
            "Diamond Pickaxe",
            "Workhorse",
            1,
            1,
            new ItemDurabilityInfo(1500, 1561, 61),
            enchantments,
            components);
    enchantments.clear();

    assertEquals("Workhorse", item.customName());
    assertEquals(1500, item.durability().remaining());
    assertEquals(
        List.of("minecraft:efficiency", "minecraft:unbreaking"),
        item.enchantments().stream().map(ItemEnchantmentInfo::enchantmentId).toList());
    assertThrows(UnsupportedOperationException.class, () -> item.enchantments().clear());
    assertThrows(IllegalArgumentException.class, () -> new ItemDurabilityInfo(10, 20, 5));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ItemStackInfo(
                "minecraft:diamond_pickaxe",
                "Diamond Pickaxe",
                null,
                1,
                1,
                null,
                List.of(
                    new ItemEnchantmentInfo("minecraft:efficiency", 4),
                    new ItemEnchantmentInfo("minecraft:efficiency", 5)),
                null));
  }

  @Test
  void equipmentAndEntityAbsenceConventionsAreExplicit() {
    EquipmentSnapshot equipment = equipmentWithMainHand(item("minecraft:stick", "Stick", 1, 64));

    assertEquals(6, equipment.slots().size());
    assertNull(
        equipment.slots().stream()
            .filter(slot -> slot.slot() == EquipmentPosition.HEAD)
            .findFirst()
            .orElseThrow()
            .item());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EntityInfo(
                "minecraft:armor_stand",
                "Armor Stand",
                null,
                2,
                new Position(0, 64, 0),
                false,
                20.0,
                20.0,
                null));
    EntityInfo zombie =
        new EntityInfo(
            "minecraft:zombie",
            "Zombie",
            null,
            4,
            new Position(4, 64, 0),
            true,
            18.0,
            20.0,
            EntityClassification.HOSTILE);
    assertTrue(zombie.living());
    assertEquals(EntityClassification.HOSTILE, zombie.classification());
  }

  @Test
  void blockEntitySnapshotsEnforceThePayloadItemCap() {
    ItemStackInfo stone = item("minecraft:stone", "Stone", 1, 64);
    List<BlockEntityItemInfo> items =
        java.util.stream.IntStream.rangeClosed(0, BlockEntityInfo.MAX_ITEMS)
            .mapToObj(slot -> new BlockEntityItemInfo(Integer.toString(slot), stone))
            .toList();

    assertThrows(
        IllegalArgumentException.class,
        () -> new BlockEntityInfo("minecraft:chest", items.size(), items, Map.of()));
  }

  @Test
  void nearbyContainerResultsAreDetachedOrderedAndSummaryBounded() {
    NearbyContainerSummary farther =
        new NearbyContainerSummary(
            "minecraft:barrel",
            "minecraft:barrel",
            "Barrel",
            new BlockPosition(4, 64, 0),
            4,
            27,
            0,
            List.of(),
            false);
    NearbyContainerSummary nearer =
        new NearbyContainerSummary(
            "minecraft:chest",
            "minecraft:chest",
            "Chest",
            new BlockPosition(1, 64, 0),
            1,
            27,
            null,
            List.of(),
            false);

    NearbyContainerResult result = new NearbyContainerResult(8, 2, false, List.of(farther, nearer));

    assertEquals(
        List.of("minecraft:chest", "minecraft:barrel"),
        result.containers().stream().map(NearbyContainerSummary::blockId).toList());
    assertThrows(
        IllegalArgumentException.class,
        () -> new NearbyContainerResult(8, 1, true, List.of(nearer, farther)));
  }

  @Test
  void recipeIngredientAlternativesRemainCompleteAndDeterministic() {
    RecipeIngredientInfo ingredient =
        new RecipeIngredientInfo(
            List.of("minecraft:oak_planks", "minecraft:birch_planks", "minecraft:oak_planks"),
            List.of("minecraft:planks"),
            2);

    assertEquals(List.of("minecraft:birch_planks", "minecraft:oak_planks"), ingredient.itemIds());
    assertEquals(List.of("minecraft:planks"), ingredient.tagIds());
    assertEquals(2, ingredient.count());
    assertThrows(
        IllegalArgumentException.class,
        () -> new RecipeIngredientInfo(List.of(), List.of("minecraft:planks"), 1));
  }

  @Test
  void errorsDetachAndSortDiagnosticDetails() {
    Map<String, String> source = new LinkedHashMap<>();
    source.put("zulu", "last");
    source.put("alpha", "first");

    ToolError error = new ToolError(ToolErrorCode.INVALID_INPUT, "Invalid.", false, source);
    source.clear();

    assertEquals(List.of("alpha", "zulu"), error.details().keySet().stream().toList());
    assertThrows(UnsupportedOperationException.class, () -> error.details().clear());
  }

  @Test
  void toolResultsRequireExactlyOneOutcome() {
    ToolError error = ToolError.of(ToolErrorCode.NOT_AVAILABLE, "Unavailable.", true);

    assertEquals("ok", ToolResult.success("ok").value());
    assertEquals(error, ToolResult.failure(error).error());
    assertThrows(IllegalArgumentException.class, () -> new ToolResult<>(null, null));
    assertThrows(IllegalArgumentException.class, () -> new ToolResult<>("value", error));
  }

  @Test
  void onlyLoadedSingleplayerCanBeReportedAsSupported() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SessionStatus(SessionState.MULTIPLAYER, true, true, true, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SessionStatus(SessionState.MAIN_MENU, false, false, false, null));
  }

  @Test
  void worldInfoKeepsWeatherSpawnAndTimeSemanticsExplicit() {
    WorldInfo clear = worldInfo(false, false, "minecraft:overworld", "minecraft:overworld", 0.0);
    WorldInfo rain = worldInfo(true, false, "minecraft:overworld", "minecraft:overworld", 0.0);
    WorldInfo thunder = worldInfo(true, true, "minecraft:overworld", "minecraft:overworld", 0.0);
    WorldInfo crossDimension =
        worldInfo(false, false, "minecraft:the_nether", "minecraft:overworld", null);

    assertFalse(clear.raining());
    assertTrue(rain.raining());
    assertTrue(thunder.thundering());
    assertNull(crossDimension.distanceFromSpawn());
    assertEquals(1, clear.worldDay());
    assertEquals(1_000, clear.timeOfDayTicks());
    assertThrows(
        IllegalArgumentException.class,
        () -> worldInfo(false, true, "minecraft:overworld", "minecraft:overworld", 0.0));
    assertThrows(
        IllegalArgumentException.class,
        () -> worldInfo(false, false, "minecraft:the_nether", "minecraft:overworld", 12.0));
  }

  private static ItemStackInfo item(String itemId, String displayName, int count, int maxCount) {
    return new ItemStackInfo(itemId, displayName, null, count, maxCount, null, List.of(), null);
  }

  private static EquipmentSnapshot equipmentWithMainHand(ItemStackInfo mainHand) {
    return new EquipmentSnapshot(
        List.of(
            new EquipmentSlotInfo(EquipmentPosition.MAIN_HAND, mainHand),
            new EquipmentSlotInfo(EquipmentPosition.OFF_HAND, null),
            new EquipmentSlotInfo(EquipmentPosition.HEAD, null),
            new EquipmentSlotInfo(EquipmentPosition.CHEST, null),
            new EquipmentSlotInfo(EquipmentPosition.LEGS, null),
            new EquipmentSlotInfo(EquipmentPosition.FEET, null)));
  }

  private static WorldInfo worldInfo(
      boolean raining,
      boolean thundering,
      String dimensionId,
      String spawnDimensionId,
      Double spawnDistance) {
    return new WorldInfo(
        dimensionId,
        "minecraft:plains",
        "Plains",
        new Position(0.5, 64.5, 0.5),
        spawnDimensionId,
        new BlockPosition(0, 64, 0),
        spawnDistance,
        "normal",
        false,
        25_000,
        25_000,
        1,
        1_000,
        DaylightState.DAY,
        raining,
        thundering,
        15,
        "waning_gibbous",
        0.8,
        true);
  }
}
