package me.clutchy.thread.core.service.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.item.find.FindItemQuery;
import me.clutchy.thread.core.model.item.find.FindItemResult;
import me.clutchy.thread.core.model.item.find.FoundItem;
import me.clutchy.thread.core.model.item.find.FoundItemSource;
import me.clutchy.thread.core.model.item.find.FoundItemSourceType;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.player.EquipmentSlotInfo;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySlotInfo;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockEntityItemInfo;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.ContainerInspectionQuery;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyContainerSnapshotResult;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class ItemFinderTest {
  private static final FindItemQuery COAL_QUERY = new FindItemQuery("coal", 16, 8, 16);

  @Test
  void aggregatesDuplicateStacksAcrossPlayerAndDistanceOrderedContainers() {
    PlayerProvider player =
        player(
            List.of(
                new InventorySlotInfo(4, item("minecraft:coal", "Coal", null, 3, 64)),
                new InventorySlotInfo(7, item("minecraft:coal", "Coal", null, 4, 64))),
            emptyEquipment());
    WorldProvider world =
        world(
            true,
            List.of(
                container(
                    "minecraft:chest",
                    new BlockPosition(4, 64, 0),
                    4,
                    List.of(
                        new BlockEntityItemInfo("2", item("minecraft:coal", "Coal", null, 5, 64))),
                    Map.of()),
                container(
                    "minecraft:hopper",
                    new BlockPosition(2, 64, 0),
                    2,
                    List.of(
                        new BlockEntityItemInfo("0", item("minecraft:coal", "Coal", null, 64, 64)),
                        new BlockEntityItemInfo("1", item("minecraft:coal", "Coal", null, 8, 64))),
                    Map.of())));

    FindItemResult result = successful(ItemFinder.vanilla(player, world).find(COAL_QUERY));
    FoundItem coal = result.matches().getFirst();

    assertEquals(84, coal.totalCount());
    assertTrue(result.containersTruncated());
    assertFalse(result.itemsTruncated());
    assertEquals(
        List.of(
            FoundItemSourceType.PLAYER_INVENTORY,
            FoundItemSourceType.NEARBY_CONTAINER,
            FoundItemSourceType.NEARBY_CONTAINER),
        coal.sources().stream().map(FoundItemSource::sourceType).toList());
    assertEquals(List.of(4, 7), coal.sources().get(0).inventorySlots());
    assertEquals(7, coal.sources().get(0).count());
    assertEquals(new BlockPosition(2, 64, 0), coal.sources().get(1).containerPosition());
    assertEquals(List.of("0", "1"), coal.sources().get(1).containerSlots());
    assertEquals(72, coal.sources().get(1).count());
    assertEquals(new BlockPosition(4, 64, 0), coal.sources().get(2).containerPosition());
  }

  @Test
  void exactRegistryIdsDoNotMatchPrefixesWhileTextAndCustomNamesStayFriendly() {
    PlayerProvider player =
        player(
            List.of(
                new InventorySlotInfo(0, item("minecraft:diamond", "Diamond", null, 3, 64)),
                new InventorySlotInfo(
                    1, item("minecraft:diamond_block", "Block of Diamond", null, 2, 64)),
                new InventorySlotInfo(2, item("minecraft:dirt", "Dirt", "Emergency Block", 1, 64))),
            emptyEquipment());
    ItemFinder finder = ItemFinder.vanilla(player, world(false, List.of()));

    FindItemResult exact =
        successful(finder.find(new FindItemQuery("minecraft:diamond", 8, 8, 16)));
    FindItemResult display = successful(finder.find(new FindItemQuery("diamond block", 8, 8, 16)));
    FindItemResult custom = successful(finder.find(new FindItemQuery("emergency", 8, 8, 16)));

    assertEquals(List.of("minecraft:diamond"), itemIds(exact));
    assertEquals(List.of("minecraft:diamond_block"), itemIds(display));
    assertEquals(List.of("minecraft:dirt"), itemIds(custom));
  }

  @Test
  void includesOffhandAndArmorWithoutCountingMainHandTwice() {
    ItemStackInfo pickaxe = item("minecraft:diamond_pickaxe", "Diamond Pickaxe", null, 1, 1);
    PlayerProvider player =
        player(
            List.of(new InventorySlotInfo(0, pickaxe)),
            equipment(
                pickaxe,
                item("minecraft:totem_of_undying", "Totem of Undying", null, 1, 1),
                item("minecraft:diamond_helmet", "Diamond Helmet", null, 1, 1)));
    ItemFinder finder = ItemFinder.vanilla(player, world(false, List.of()));

    FoundItem foundPickaxe =
        successful(finder.find(new FindItemQuery("minecraft:diamond_pickaxe", 8, 8, 16)))
            .matches()
            .getFirst();
    FoundItem foundTotem =
        successful(finder.find(new FindItemQuery("totem", 8, 8, 16))).matches().getFirst();
    FoundItem foundHelmet =
        successful(finder.find(new FindItemQuery("diamond helmet", 8, 8, 16))).matches().getFirst();

    assertEquals(1, foundPickaxe.totalCount());
    assertEquals(
        FoundItemSourceType.PLAYER_INVENTORY, foundPickaxe.sources().getFirst().sourceType());
    assertEquals(
        List.of(EquipmentPosition.OFF_HAND), foundTotem.sources().getFirst().equipmentSlots());
    assertEquals(
        List.of(EquipmentPosition.HEAD), foundHelmet.sources().getFirst().equipmentSlots());
  }

  @Test
  void ignoresUnresolvedAndTruncatedContainerContentsAndReturnsNoMatchCleanly() {
    ItemStackInfo hidden = item("minecraft:nether_star", "Nether Star", null, 1, 64);
    WorldProvider world =
        world(
            false,
            List.of(
                container(
                    "minecraft:chest",
                    new BlockPosition(2, 64, 0),
                    2,
                    List.of(new BlockEntityItemInfo("0", hidden)),
                    Map.of("contentsResolved", "false")),
                container(
                    "minecraft:chest",
                    new BlockPosition(3, 64, 0),
                    3,
                    List.of(new BlockEntityItemInfo("0", hidden)),
                    Map.of("itemsTruncated", "true"))));

    FindItemResult result =
        successful(
            ItemFinder.vanilla(player(List.of(), emptyEquipment()), world)
                .find(new FindItemQuery("nether star", 8, 8, 16)));

    assertTrue(result.matches().isEmpty());
    assertFalse(result.itemsTruncated());
  }

  @Test
  void sortsItemIdentitiesAndReportsItemAndProviderLimits() {
    PlayerProvider player =
        player(
            List.of(
                new InventorySlotInfo(0, item("minecraft:stone", "Stone", null, 1, 64)),
                new InventorySlotInfo(1, item("minecraft:sand", "Sand", null, 1, 64))),
            emptyEquipment());
    ItemFinder finder = ItemFinder.vanilla(player, world(false, List.of()));

    FindItemResult truncated = successful(finder.find(new FindItemQuery("minecraft", 8, 8, 1)));
    ToolResult<FindItemResult> tooMany =
        finder.find(new FindItemQuery("minecraft", 8, 8, ItemFinder.MAX_ITEM_RESULTS + 1));
    ToolResult<FindItemResult> outOfRange =
        ItemFinder.vanilla(player, failingWorld(ToolErrorCode.OUT_OF_RANGE))
            .find(new FindItemQuery("stone", 17, 8, 16));

    assertEquals(List.of("minecraft:sand"), itemIds(truncated));
    assertTrue(truncated.itemsTruncated());
    assertEquals(ToolErrorCode.RESULT_LIMIT_EXCEEDED, tooMany.error().code());
    assertEquals(ToolErrorCode.OUT_OF_RANGE, outOfRange.error().code());
  }

  private static PlayerProvider player(
      List<InventorySlotInfo> inventory, EquipmentSnapshot equipment) {
    return new PlayerProvider() {
      @Override
      public ToolResult<PlayerStatus> status() {
        throw new AssertionError("status was not expected");
      }

      @Override
      public ToolResult<InventorySnapshot> inventory() {
        return ToolResult.success(new InventorySnapshot(0, inventory));
      }

      @Override
      public ToolResult<EquipmentSnapshot> equipment() {
        return ToolResult.success(equipment);
      }

      @Override
      public ToolResult<Optional<BlockInfo>> targetBlock() {
        return ToolResult.success(Optional.empty());
      }
    };
  }

  private static WorldProvider world(boolean truncated, List<BlockInfo> containers) {
    return new WorldProvider() {
      @Override
      public ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query) {
        throw new AssertionError("nearby entities were not expected");
      }

      @Override
      public ToolResult<NearbyContainerResult> nearbyContainers(NearbyContainerQuery query) {
        throw new AssertionError("summary container search was not expected");
      }

      @Override
      public ToolResult<NearbyContainerSnapshotResult> nearbyContainerSnapshots(
          NearbyContainerQuery query) {
        return ToolResult.success(
            new NearbyContainerSnapshotResult(
                query.radius(), query.limit(), truncated, containers));
      }

      @Override
      public ToolResult<BlockInfo> inspectContainer(ContainerInspectionQuery query) {
        throw new AssertionError("individual inspection was not expected");
      }
    };
  }

  private static WorldProvider failingWorld(ToolErrorCode code) {
    ToolError error = ToolError.of(code, "Unavailable for test.", true);
    return new WorldProvider() {
      @Override
      public ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query) {
        return ToolResult.failure(error);
      }

      @Override
      public ToolResult<NearbyContainerResult> nearbyContainers(NearbyContainerQuery query) {
        return ToolResult.failure(error);
      }

      @Override
      public ToolResult<NearbyContainerSnapshotResult> nearbyContainerSnapshots(
          NearbyContainerQuery query) {
        return ToolResult.failure(error);
      }

      @Override
      public ToolResult<BlockInfo> inspectContainer(ContainerInspectionQuery query) {
        return ToolResult.failure(error);
      }
    };
  }

  private static BlockInfo container(
      String typeId,
      BlockPosition position,
      double distance,
      List<BlockEntityItemInfo> items,
      Map<String, String> state) {
    return new BlockInfo(
        typeId,
        typeId.substring(typeId.indexOf(':') + 1),
        position,
        Map.of(),
        distance,
        true,
        new BlockEntityInfo(typeId, 27, items, state));
  }

  private static EquipmentSnapshot emptyEquipment() {
    return equipment(null, null, null);
  }

  private static EquipmentSnapshot equipment(
      ItemStackInfo mainHand, ItemStackInfo offHand, ItemStackInfo head) {
    return new EquipmentSnapshot(
        List.of(
            new EquipmentSlotInfo(EquipmentPosition.MAIN_HAND, mainHand),
            new EquipmentSlotInfo(EquipmentPosition.OFF_HAND, offHand),
            new EquipmentSlotInfo(EquipmentPosition.HEAD, head),
            new EquipmentSlotInfo(EquipmentPosition.CHEST, null),
            new EquipmentSlotInfo(EquipmentPosition.LEGS, null),
            new EquipmentSlotInfo(EquipmentPosition.FEET, null)));
  }

  private static ItemStackInfo item(
      String itemId, String displayName, String customName, int count, int maxCount) {
    return new ItemStackInfo(
        itemId, displayName, customName, count, maxCount, null, List.of(), null);
  }

  private static List<String> itemIds(FindItemResult result) {
    return result.matches().stream().map(match -> match.item().itemId()).toList();
  }

  private static <T> T successful(ToolResult<T> result) {
    assertTrue(result.successful(), () -> Objects.toString(result.error()));
    return Objects.requireNonNull(result.value());
  }
}
