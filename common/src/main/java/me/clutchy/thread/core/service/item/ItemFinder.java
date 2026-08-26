package me.clutchy.thread.core.service.item;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.item.ItemInfo;
import me.clutchy.thread.core.model.item.find.FindItemQuery;
import me.clutchy.thread.core.model.item.find.FindItemResult;
import me.clutchy.thread.core.model.item.find.FoundItem;
import me.clutchy.thread.core.model.item.find.FoundItemSource;
import me.clutchy.thread.core.model.item.find.FoundItemSourceType;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.ToolResult;

/** Aggregates deterministic live item matches from small transport-independent item sources. */
public final class ItemFinder {
  /** Maximum distinct matching item identities returned from one live search. */
  public static final int MAX_ITEM_RESULTS = 64;

  private static final Comparator<SourceKey> SOURCE_ORDER =
      Comparator.comparing(SourceKey::sourceType)
          .thenComparing(key -> key.distance() == null ? -1D : key.distance())
          .thenComparing(key -> key.containerPosition() == null ? 0 : key.containerPosition().x())
          .thenComparing(key -> key.containerPosition() == null ? 0 : key.containerPosition().y())
          .thenComparing(key -> key.containerPosition() == null ? 0 : key.containerPosition().z())
          .thenComparing(key -> key.containerTypeId() == null ? "" : key.containerTypeId());

  private final List<ItemSource> sources;

  /** Creates a finder over an explicit ordered collection of bounded item sources. */
  public ItemFinder(List<ItemSource> sources) {
    this.sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    if (this.sources.isEmpty()) {
      throw new IllegalArgumentException("sources must not be empty");
    }
  }

  /** Creates the built-in player and nearby-loaded-container source set. */
  public static ItemFinder vanilla(PlayerProvider player, WorldProvider world) {
    return new ItemFinder(
        List.of(
            ItemSources.playerInventory(player),
            ItemSources.playerEquipment(player),
            ItemSources.nearbyContainers(world)));
  }

  /** Returns matched item identities with totals and deterministic source locations. */
  public ToolResult<FindItemResult> find(FindItemQuery query) {
    Objects.requireNonNull(query, "query");
    if (query.itemLimit() > MAX_ITEM_RESULTS) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.RESULT_LIMIT_EXCEEDED,
              "The requested item result limit exceeds Thread's safety limit.",
              false,
              Map.of(
                  "requested", Integer.toString(query.itemLimit()),
                  "limit", Integer.toString(MAX_ITEM_RESULTS))));
    }

    TreeMap<String, MatchAccumulator> matches = new TreeMap<>();
    boolean containersTruncated = false;
    ItemSource.Request request =
        ItemSource.Request.nearby(new NearbyContainerQuery(query.radius(), query.containerLimit()));
    for (ItemSource source : sources) {
      ToolResult<ItemSource.Snapshot> result = source.read(request);
      if (!result.successful()) {
        return ToolResult.failure(Objects.requireNonNull(result.error()));
      }
      ItemSource.Snapshot snapshot = Objects.requireNonNull(result.value());
      containersTruncated |= snapshot.containersTruncated();
      for (ItemSource.Entry entry : snapshot.entries()) {
        if (!ItemTextMatcher.matches(
            query.query(),
            entry.item().itemId(),
            entry.item().displayName(),
            entry.item().customName())) {
          continue;
        }
        matches
            .computeIfAbsent(
                entry.item().itemId(),
                ignored ->
                    new MatchAccumulator(
                        new ItemInfo(entry.item().itemId(), entry.item().displayName())))
            .add(entry);
      }
    }

    boolean itemsTruncated = matches.size() > query.itemLimit();
    List<FoundItem> returned =
        matches.values().stream().limit(query.itemLimit()).map(MatchAccumulator::result).toList();
    return ToolResult.success(
        new FindItemResult(
            query.query(),
            query.radius(),
            query.containerLimit(),
            query.itemLimit(),
            containersTruncated,
            itemsTruncated,
            returned));
  }

  private static final class MatchAccumulator {
    private final ItemInfo item;
    private final TreeMap<SourceKey, SourceAccumulator> sources = new TreeMap<>(SOURCE_ORDER);

    private MatchAccumulator(ItemInfo item) {
      this.item = item;
    }

    private void add(ItemSource.Entry entry) {
      SourceKey key =
          new SourceKey(
              entry.sourceType(),
              entry.containerPosition(),
              entry.containerTypeId(),
              entry.distance());
      sources.computeIfAbsent(key, SourceAccumulator::new).add(entry);
    }

    private FoundItem result() {
      List<FoundItemSource> sourceResults =
          sources.values().stream().map(SourceAccumulator::result).toList();
      int total = sourceResults.stream().mapToInt(FoundItemSource::count).sum();
      return new FoundItem(item, total, sourceResults);
    }
  }

  private static final class SourceAccumulator {
    private final SourceKey key;
    private int count;
    private final TreeSet<Integer> inventorySlots = new TreeSet<>();
    private final EnumSet<EquipmentPosition> equipmentSlots =
        EnumSet.noneOf(EquipmentPosition.class);
    private final TreeSet<String> containerSlots = new TreeSet<>();

    private SourceAccumulator(SourceKey key) {
      this.key = key;
    }

    private void add(ItemSource.Entry entry) {
      count = Math.addExact(count, entry.item().count());
      if (entry.inventorySlot() != null) {
        inventorySlots.add(entry.inventorySlot());
      }
      if (entry.equipmentSlot() != null) {
        equipmentSlots.add(entry.equipmentSlot());
      }
      if (entry.containerSlot() != null) {
        containerSlots.add(entry.containerSlot());
      }
    }

    private FoundItemSource result() {
      return new FoundItemSource(
          key.sourceType(),
          count,
          List.copyOf(inventorySlots),
          List.copyOf(equipmentSlots),
          List.copyOf(containerSlots),
          key.containerPosition(),
          key.containerTypeId(),
          key.distance());
    }
  }

  private record SourceKey(
      FoundItemSourceType sourceType,
      BlockPosition containerPosition,
      String containerTypeId,
      Double distance) {}
}
