package me.clutchy.thread.core.service.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import me.clutchy.thread.core.model.crafting.CraftingScope;
import me.clutchy.thread.core.model.crafting.CraftingSourceStatus;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.ToolResult;

/** Builds one deterministic detached item snapshot for each scoped crafting invocation. */
public final class CraftingItemSourceProvider {
  private static final Comparator<ItemSource.Entry> ENTRY_ORDER =
      Comparator.comparing(ItemSource.Entry::sourceType)
          .thenComparing(entry -> entry.distance() == null ? -1D : entry.distance())
          .thenComparing(
              entry -> entry.containerPosition() == null ? 0 : entry.containerPosition().x())
          .thenComparing(
              entry -> entry.containerPosition() == null ? 0 : entry.containerPosition().y())
          .thenComparing(
              entry -> entry.containerPosition() == null ? 0 : entry.containerPosition().z())
          .thenComparing(entry -> entry.inventorySlot() == null ? -1 : entry.inventorySlot())
          .thenComparing(entry -> entry.equipmentSlot() == null ? "" : entry.equipmentSlot().name())
          .thenComparing(entry -> entry.containerSlot() == null ? "" : entry.containerSlot())
          .thenComparing(entry -> entry.item().itemId());

  private final ItemSource playerInventory;
  private final List<ItemSource> expandedSources;
  private final Supplier<NearbyContainerQuery> nearbyBounds;

  /** Creates an explicit source composition usable by future integration wiring. */
  public CraftingItemSourceProvider(
      ItemSource playerInventory,
      List<ItemSource> expandedSources,
      Supplier<NearbyContainerQuery> nearbyBounds) {
    this.playerInventory = Objects.requireNonNull(playerInventory, "playerInventory");
    this.expandedSources = List.copyOf(Objects.requireNonNull(expandedSources, "expandedSources"));
    this.nearbyBounds = Objects.requireNonNull(nearbyBounds, "nearbyBounds");
  }

  /** Creates the built-in player plus nearby-loaded-container crafting source set. */
  public static CraftingItemSourceProvider vanilla(
      PlayerProvider player, WorldProvider world, NearbyContainerQuery nearbyBounds) {
    ItemSource inventory = ItemSources.playerInventory(player);
    return new CraftingItemSourceProvider(
        inventory,
        List.of(ItemSources.nearbyContainers(world)),
        () -> Objects.requireNonNull(nearbyBounds, "nearbyBounds"));
  }

  /** Creates a player-only source set for focused core tests and internal consumers. */
  public static CraftingItemSourceProvider playerOnly(PlayerProvider player) {
    return new CraftingItemSourceProvider(
        ItemSources.playerInventory(player), List.of(), () -> new NearbyContainerQuery(1, 1));
  }

  /** Captures all sources eligible for the requested scope exactly once. */
  public ToolResult<Snapshot> snapshot(CraftingScope scope) {
    Objects.requireNonNull(scope, "scope");
    ToolResult<ItemSource.Snapshot> playerResult =
        playerInventory.read(ItemSource.Request.playerOnly());
    if (!playerResult.successful()) {
      return ToolResult.failure(Objects.requireNonNull(playerResult.error()));
    }
    ItemSource.Snapshot playerSnapshot = Objects.requireNonNull(playerResult.value());
    List<ItemSource.Entry> entries = new ArrayList<>(playerSnapshot.entries());
    if (scope == CraftingScope.PLAYER_ONLY) {
      return ToolResult.success(
          new Snapshot(
              scope,
              new CraftingSourceStatus(
                  playerSnapshot.complete(),
                  null,
                  null,
                  playerSnapshot.containersTruncated(),
                  playerSnapshot.unresolvedContainersSkipped(),
                  playerSnapshot.contentLimitedContainersSkipped()),
              sorted(entries)));
    }

    NearbyContainerQuery bounds = Objects.requireNonNull(nearbyBounds.get(), "nearbyBounds value");
    boolean complete = playerSnapshot.complete();
    boolean truncated = playerSnapshot.containersTruncated();
    int unresolved = playerSnapshot.unresolvedContainersSkipped();
    int contentLimited = playerSnapshot.contentLimitedContainersSkipped();
    ItemSource.Request request = ItemSource.Request.nearby(bounds);
    for (ItemSource source : expandedSources) {
      ToolResult<ItemSource.Snapshot> result = source.read(request);
      if (!result.successful()) {
        return ToolResult.failure(Objects.requireNonNull(result.error()));
      }
      ItemSource.Snapshot snapshot = Objects.requireNonNull(result.value());
      entries.addAll(snapshot.entries());
      complete &= snapshot.complete();
      truncated |= snapshot.containersTruncated();
      unresolved = Math.addExact(unresolved, snapshot.unresolvedContainersSkipped());
      contentLimited = Math.addExact(contentLimited, snapshot.contentLimitedContainersSkipped());
    }
    CraftingSourceStatus status =
        new CraftingSourceStatus(
            complete, bounds.radius(), bounds.limit(), truncated, unresolved, contentLimited);
    return ToolResult.success(new Snapshot(scope, status, sorted(entries)));
  }

  private static List<ItemSource.Entry> sorted(List<ItemSource.Entry> entries) {
    return entries.stream().sorted(ENTRY_ORDER).toList();
  }

  /** One immutable source snapshot and the completeness metadata attached to its result. */
  public record Snapshot(
      CraftingScope scope, CraftingSourceStatus sourceStatus, List<ItemSource.Entry> entries) {
    public Snapshot {
      Objects.requireNonNull(scope, "scope");
      Objects.requireNonNull(sourceStatus, "sourceStatus");
      entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
  }
}
