package me.clutchy.thread.platform.minecraft.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockEntityItemInfo;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyContainerSnapshotResult;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.Position;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.minecraft.game.MinecraftProviderLimits;
import org.junit.jupiter.api.Test;

class MinecraftWorldProviderTest {
  private static final MinecraftProviderLimits LIMITS = MinecraftProviderLimits.defaults();

  @Test
  void enforcesRadiusAndResultCapsBeforeGameThreadDispatch() {
    assertEquals(
        ToolErrorCode.OUT_OF_RANGE,
        MinecraftWorldProvider.validateQuery(new NearbyEntityQuery(65, 64), LIMITS)
            .orElseThrow()
            .code());
    assertEquals(
        ToolErrorCode.RESULT_LIMIT_EXCEEDED,
        MinecraftWorldProvider.validateQuery(new NearbyEntityQuery(16, 129), LIMITS)
            .orElseThrow()
            .code());
    assertTrue(
        MinecraftWorldProvider.validateQuery(new NearbyEntityQuery(16, 64), LIMITS).isEmpty());
    assertEquals(
        ToolErrorCode.OUT_OF_RANGE,
        MinecraftWorldProvider.validateContainerQuery(new NearbyContainerQuery(17, 32), LIMITS)
            .orElseThrow()
            .code());
    assertEquals(
        ToolErrorCode.RESULT_LIMIT_EXCEEDED,
        MinecraftWorldProvider.validateContainerQuery(new NearbyContainerQuery(8, 65), LIMITS)
            .orElseThrow()
            .code());
    assertTrue(
        MinecraftWorldProvider.validateContainerQuery(new NearbyContainerQuery(16, 64), LIMITS)
            .isEmpty());
  }

  @Test
  void returnsEmptyAndMultipleContainersInDistanceOrder() {
    Position origin = new Position(0.5, 64.5, 0.5);
    FakeContainerAccess empty = new FakeContainerAccess();

    NearbyContainerResult none =
        MinecraftWorldProvider.findNearbyContainers(new NearbyContainerQuery(8, 8), origin, empty);

    assertTrue(none.containers().isEmpty());
    assertFalse(none.truncated());

    FakeContainerAccess access = new FakeContainerAccess();
    access.add(new BlockPosition(5, 64, 0), container("minecraft:barrel", 27, List.of()));
    access.add(new BlockPosition(1, 64, 0), container("minecraft:chest", 27, List.of()));

    NearbyContainerResult result =
        MinecraftWorldProvider.findNearbyContainers(new NearbyContainerQuery(8, 8), origin, access);

    assertEquals(
        List.of("minecraft:chest", "minecraft:barrel"),
        result.containers().stream().map(container -> container.blockId()).toList());
    assertTrue(result.containers().get(0).distance() < result.containers().get(1).distance());
  }

  @Test
  void resultLimitStopsAfterOneExtraContainerAndMarksTruncation() {
    Position origin = new Position(0.5, 64.5, 0.5);
    FakeContainerAccess access = new FakeContainerAccess();
    access.add(new BlockPosition(1, 64, 0), container("minecraft:chest", 27, List.of()));
    access.add(new BlockPosition(2, 64, 0), container("minecraft:barrel", 27, List.of()));

    NearbyContainerResult result =
        MinecraftWorldProvider.findNearbyContainers(new NearbyContainerQuery(8, 1), origin, access);

    assertEquals(1, result.containers().size());
    assertTrue(result.truncated());
    assertTrue(access.inspections() > 1);
  }

  @Test
  void fullSnapshotsPreserveBoundedContainerItemsAndDistanceOrder() {
    Position origin = new Position(0.5, 64.5, 0.5);
    FakeContainerAccess access = new FakeContainerAccess();
    ItemStackInfo coal = item("minecraft:coal", "Coal", 5, 64);
    access.add(
        new BlockPosition(4, 64, 0),
        container("minecraft:barrel", 27, List.of(new BlockEntityItemInfo("4", coal))));
    access.add(
        new BlockPosition(1, 64, 0),
        container("minecraft:chest", 27, List.of(new BlockEntityItemInfo("2", coal))));

    NearbyContainerSnapshotResult result =
        MinecraftWorldProvider.findNearbyContainerSnapshots(
            new NearbyContainerQuery(8, 8), origin, access);

    assertEquals(
        List.of("minecraft:chest", "minecraft:barrel"),
        result.containers().stream().map(BlockInfo::blockId).toList());
    assertEquals("2", result.containers().getFirst().blockEntity().items().getFirst().slot());
    assertEquals(5, result.containers().getFirst().blockEntity().items().getFirst().item().count());
  }

  @Test
  void fullSnapshotsSkipUnloadedAndOutOfRadiusContainers() {
    Position origin = new Position(0.5, 64.5, 0.5);
    FakeContainerAccess access = new FakeContainerAccess();
    BlockPosition loaded = new BlockPosition(1, 64, 0);
    BlockPosition unloaded = new BlockPosition(2, 64, 0);
    BlockPosition outOfRange = new BlockPosition(9, 64, 0);
    access.add(loaded, container("minecraft:chest", 27, List.of()));
    access.add(unloaded, container("minecraft:barrel", 27, List.of()));
    access.add(outOfRange, container("minecraft:hopper", 5, List.of()));
    access.unload(unloaded);

    NearbyContainerSnapshotResult result =
        MinecraftWorldProvider.findNearbyContainerSnapshots(
            new NearbyContainerQuery(8, 8), origin, access);

    assertEquals(
        List.of("minecraft:chest"), result.containers().stream().map(BlockInfo::blockId).toList());
  }

  @Test
  void distinguishesUnloadedAndLoadedNonContainerPositions() {
    BlockPosition position = new BlockPosition(1, 64, 0);
    FakeContainerAccess access = new FakeContainerAccess();
    access.unload(position);

    ToolResult<BlockInfo> unloaded =
        MinecraftWorldProvider.inspectLoadedContainer(position, 1, access);

    assertEquals(ToolErrorCode.NOT_AVAILABLE, unloaded.error().code());
    assertEquals(0, access.inspections());

    access.load(position);
    ToolResult<BlockInfo> nonContainer =
        MinecraftWorldProvider.inspectLoadedContainer(position, 1, access);

    assertEquals(ToolErrorCode.NOT_FOUND, nonContainer.error().code());
    assertEquals(1, access.inspections());
  }

  @Test
  void summarizesEmptyAndFullInventoriesWithoutDumpingEverySlot() {
    Position origin = new Position(0.5, 64.5, 0.5);
    FakeContainerAccess access = new FakeContainerAccess();
    access.add(new BlockPosition(1, 64, 0), container("minecraft:barrel", 27, List.of()));
    ItemStackInfo stone = item("minecraft:stone", "Stone", 64, 64);
    List<BlockEntityItemInfo> full =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(slot -> new BlockEntityItemInfo(Integer.toString(slot), stone))
            .toList();
    access.add(new BlockPosition(2, 64, 0), container("minecraft:hopper", 5, full));

    NearbyContainerResult result =
        MinecraftWorldProvider.findNearbyContainers(new NearbyContainerQuery(8, 8), origin, access);

    assertEquals(0, result.containers().get(0).usedSlotCount());
    assertTrue(result.containers().get(0).itemSummary().isEmpty());
    assertEquals(5, result.containers().get(1).usedSlotCount());
    assertEquals(4, result.containers().get(1).itemSummary().size());
    assertTrue(result.containers().get(1).itemSummaryTruncated());

    BlockInfo unresolved =
        container(
            "minecraft:chest",
            27,
            List.of(),
            Map.of("contentsResolved", "false", "lootTable", "minecraft:test"));
    access.add(new BlockPosition(3, 64, 0), unresolved);
    NearbyContainerResult withUnresolved =
        MinecraftWorldProvider.findNearbyContainers(new NearbyContainerQuery(8, 8), origin, access);
    assertNull(withUnresolved.containers().get(2).usedSlotCount());
  }

  private static BlockInfo container(String id, int slots, List<BlockEntityItemInfo> items) {
    return container(id, slots, items, Map.of());
  }

  private static BlockInfo container(
      String id, int slots, List<BlockEntityItemInfo> items, Map<String, String> state) {
    return new BlockInfo(
        id,
        id.substring(id.indexOf(':') + 1),
        new BlockPosition(0, 0, 0),
        Map.of(),
        0,
        true,
        new BlockEntityInfo(id, slots, items, state));
  }

  private static ItemStackInfo item(String id, String displayName, int count, int maxCount) {
    return new ItemStackInfo(id, displayName, null, count, maxCount, null, List.of(), null);
  }

  private static final class FakeContainerAccess
      implements MinecraftWorldProvider.LoadedContainerAccess {
    private final Map<BlockPosition, BlockInfo> containers = new HashMap<>();
    private final Set<BlockPosition> unloaded = new HashSet<>();
    private int inspections;

    void add(BlockPosition position, BlockInfo block) {
      containers.put(position, block);
    }

    void unload(BlockPosition position) {
      unloaded.add(position);
    }

    void load(BlockPosition position) {
      unloaded.remove(position);
    }

    int inspections() {
      return inspections;
    }

    @Override
    public boolean loaded(BlockPosition position) {
      return !unloaded.contains(position);
    }

    @Override
    public Optional<BlockInfo> inspect(BlockPosition position, double distance) {
      inspections++;
      return Optional.ofNullable(containers.get(position))
          .map(
              block ->
                  new BlockInfo(
                      block.blockId(),
                      block.displayName(),
                      position,
                      block.properties(),
                      distance,
                      true,
                      block.blockEntity()));
    }
  }
}
