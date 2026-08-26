package me.clutchy.thread.core.service.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.model.item.find.FoundItemSourceType;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.NearbyContainerSnapshotResult;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.ToolResult;

/** Built-in detached item sources shared by live search and scoped crafting. */
public final class ItemSources {
  private ItemSources() {}

  /** Returns a source that reads only the player's 36-slot main inventory. */
  public static ItemSource playerInventory(PlayerProvider player) {
    Objects.requireNonNull(player, "player");
    return request -> {
      ToolResult<InventorySnapshot> result = player.inventory();
      if (!result.successful()) {
        return ToolResult.failure(Objects.requireNonNull(result.error()));
      }
      List<ItemSource.Entry> entries =
          Objects.requireNonNull(result.value()).slots().stream()
              .map(
                  slot ->
                      new ItemSource.Entry(
                          slot.stack(),
                          FoundItemSourceType.PLAYER_INVENTORY,
                          slot.slot(),
                          null,
                          null,
                          null,
                          null,
                          null))
              .toList();
      return ToolResult.success(ItemSource.Snapshot.complete(entries));
    };
  }

  /**
   * Returns a source for offhand and armor without duplicating the selected main-hand hotbar slot.
   */
  public static ItemSource playerEquipment(PlayerProvider player) {
    Objects.requireNonNull(player, "player");
    return request -> {
      ToolResult<EquipmentSnapshot> result = player.equipment();
      if (!result.successful()) {
        return ToolResult.failure(Objects.requireNonNull(result.error()));
      }
      List<ItemSource.Entry> entries =
          Objects.requireNonNull(result.value()).slots().stream()
              .filter(slot -> slot.slot() != EquipmentPosition.MAIN_HAND && slot.item() != null)
              .map(
                  slot ->
                      new ItemSource.Entry(
                          slot.item(),
                          FoundItemSourceType.PLAYER_EQUIPMENT,
                          null,
                          slot.slot(),
                          null,
                          null,
                          null,
                          null))
              .toList();
      return ToolResult.success(ItemSource.Snapshot.complete(entries));
    };
  }

  /** Returns a source backed by one bounded loaded-container snapshot. */
  public static ItemSource nearbyContainers(WorldProvider world) {
    Objects.requireNonNull(world, "world");
    return request -> {
      if (request.nearbyContainers() == null) {
        throw new IllegalArgumentException("nearby container source requires explicit bounds");
      }
      ToolResult<NearbyContainerSnapshotResult> result =
          world.nearbyContainerSnapshots(request.nearbyContainers());
      if (!result.successful()) {
        return ToolResult.failure(Objects.requireNonNull(result.error()));
      }
      NearbyContainerSnapshotResult containers = Objects.requireNonNull(result.value());
      List<ItemSource.Entry> entries = new ArrayList<>();
      int unresolved = 0;
      int contentLimited = 0;
      for (BlockInfo container : containers.containers()) {
        BlockEntityInfo blockEntity = Objects.requireNonNull(container.blockEntity());
        if ("false".equals(blockEntity.state().get("contentsResolved"))) {
          unresolved++;
          continue;
        }
        if ("true".equals(blockEntity.state().get("itemsTruncated"))) {
          contentLimited++;
          continue;
        }
        blockEntity
            .items()
            .forEach(
                item ->
                    entries.add(
                        new ItemSource.Entry(
                            item.item(),
                            FoundItemSourceType.NEARBY_CONTAINER,
                            null,
                            null,
                            item.slot(),
                            container.position(),
                            blockEntity.typeId(),
                            container.distance())));
      }
      boolean complete = !containers.truncated() && unresolved == 0 && contentLimited == 0;
      return ToolResult.success(
          new ItemSource.Snapshot(
              complete, containers.truncated(), unresolved, contentLimited, entries));
    };
  }
}
