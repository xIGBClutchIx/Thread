package me.clutchy.thread.core.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import me.clutchy.thread.core.model.item.find.FoundItemSource;
import me.clutchy.thread.core.model.item.find.FoundItemSourceType;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.service.item.ItemSource;

/** Mutable copyable ledger for initial located supplies plus planned intermediate output. */
final class CraftingSupplyLedger {
  private static final Comparator<SourceKey> SOURCE_ORDER =
      Comparator.comparing(SourceKey::sourceType)
          .thenComparing(key -> key.distance() == null ? -1D : key.distance())
          .thenComparing(key -> key.containerPosition() == null ? 0 : key.containerPosition().x())
          .thenComparing(key -> key.containerPosition() == null ? 0 : key.containerPosition().y())
          .thenComparing(key -> key.containerPosition() == null ? 0 : key.containerPosition().z())
          .thenComparing(key -> key.containerTypeId() == null ? "" : key.containerTypeId());

  private final List<StackSupply> initial;
  private final TreeMap<String, Integer> crafted;

  CraftingSupplyLedger(List<ItemSource.Entry> entries) {
    this(
        entries.stream().map(entry -> new StackSupply(entry, entry.item().count())).toList(),
        new TreeMap<>());
  }

  private CraftingSupplyLedger(List<StackSupply> initial, TreeMap<String, Integer> crafted) {
    this.initial = new ArrayList<>(initial);
    this.crafted = crafted;
  }

  CraftingSupplyLedger copy() {
    return new CraftingSupplyLedger(
        initial.stream().map(StackSupply::copy).toList(), new TreeMap<>(crafted));
  }

  Map<String, Integer> counts() {
    TreeMap<String, Integer> counts = new TreeMap<>();
    for (StackSupply supply : initial) {
      if (supply.remaining > 0) {
        counts.merge(supply.entry.item().itemId(), supply.remaining, Math::addExact);
      }
    }
    crafted.forEach((itemId, count) -> counts.merge(itemId, count, Math::addExact));
    return counts;
  }

  Consumption consume(String itemId, int requested) {
    Objects.requireNonNull(itemId, "itemId");
    if (requested < 0) {
      throw new IllegalArgumentException("requested must not be negative");
    }
    int remaining = requested;
    int playerInventory = 0;
    TreeMap<SourceKey, SourceAccumulator> sources = new TreeMap<>(SOURCE_ORDER);
    for (StackSupply supply : initial) {
      if (remaining == 0 || supply.remaining == 0) {
        continue;
      }
      if (!supply.entry.item().itemId().equals(itemId)) {
        continue;
      }
      int consumed = Math.min(supply.remaining, remaining);
      supply.remaining -= consumed;
      remaining -= consumed;
      if (supply.entry.sourceType() == FoundItemSourceType.PLAYER_INVENTORY) {
        playerInventory = Math.addExact(playerInventory, consumed);
      }
      SourceKey key = SourceKey.from(supply.entry);
      sources.computeIfAbsent(key, SourceAccumulator::new).add(supply.entry, consumed);
    }
    int fromCrafted = take(crafted, itemId, remaining);
    remaining -= fromCrafted;
    return new Consumption(
        requested - remaining,
        playerInventory,
        fromCrafted,
        sources.values().stream().map(SourceAccumulator::result).toList());
  }

  Consumption consumeExact(String itemId, int requested) {
    Consumption consumed = consume(itemId, requested);
    if (consumed.total() != requested) {
      throw new IllegalStateException("allocation exceeded available supply for " + itemId);
    }
    return consumed;
  }

  void addCrafted(String itemId, int count) {
    crafted.merge(itemId, count, Math::addExact);
  }

  static List<FoundItemSource> mergeSources(
      List<FoundItemSource> first, List<FoundItemSource> second) {
    TreeMap<SourceKey, SourceAccumulator> merged = new TreeMap<>(SOURCE_ORDER);
    addSources(merged, first);
    addSources(merged, second);
    return merged.values().stream().map(SourceAccumulator::result).toList();
  }

  private static void addSources(
      TreeMap<SourceKey, SourceAccumulator> merged, List<FoundItemSource> sources) {
    for (FoundItemSource source : sources) {
      SourceKey key = SourceKey.from(source);
      merged.computeIfAbsent(key, SourceAccumulator::new).add(source);
    }
  }

  private static int take(Map<String, Integer> source, String itemId, int requested) {
    int available = source.getOrDefault(itemId, 0);
    int consumed = Math.min(available, requested);
    int remaining = available - consumed;
    if (remaining == 0) {
      source.remove(itemId);
    } else {
      source.put(itemId, remaining);
    }
    return consumed;
  }

  record Consumption(
      int total, int playerInventory, int crafted, List<FoundItemSource> sourceAllocations) {}

  private static final class StackSupply {
    private final ItemSource.Entry entry;
    private int remaining;

    private StackSupply(ItemSource.Entry entry, int remaining) {
      this.entry = Objects.requireNonNull(entry, "entry");
      this.remaining = remaining;
    }

    private StackSupply copy() {
      return new StackSupply(entry, remaining);
    }
  }

  private record SourceKey(
      FoundItemSourceType sourceType,
      BlockPosition containerPosition,
      String containerTypeId,
      Double distance) {
    private static SourceKey from(ItemSource.Entry entry) {
      return new SourceKey(
          entry.sourceType(), entry.containerPosition(), entry.containerTypeId(), entry.distance());
    }

    private static SourceKey from(FoundItemSource source) {
      return new SourceKey(
          source.sourceType(),
          source.containerPosition(),
          source.containerTypeId(),
          source.distance());
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

    private void add(ItemSource.Entry entry, int contributed) {
      count = Math.addExact(count, contributed);
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

    private void add(FoundItemSource source) {
      count = Math.addExact(count, source.count());
      inventorySlots.addAll(source.inventorySlots());
      equipmentSlots.addAll(source.equipmentSlots());
      containerSlots.addAll(source.containerSlots());
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
}
