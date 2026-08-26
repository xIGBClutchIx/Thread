package me.clutchy.thread.core.model.item.find;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.validation.ModelValidation;
import me.clutchy.thread.core.model.world.BlockPosition;

/** Aggregated count and structured locations for one matched item in one live source. */
public record FoundItemSource(
    FoundItemSourceType sourceType,
    int count,
    List<Integer> inventorySlots,
    List<EquipmentPosition> equipmentSlots,
    List<String> containerSlots,
    BlockPosition containerPosition,
    String containerTypeId,
    Double distance) {
  private static final Comparator<FoundItemSource> ORDER =
      Comparator.comparing(FoundItemSource::sourceType)
          .thenComparing(source -> source.distance() == null ? -1D : source.distance())
          .thenComparing(
              source -> source.containerPosition() == null ? 0 : source.containerPosition().x())
          .thenComparing(
              source -> source.containerPosition() == null ? 0 : source.containerPosition().y())
          .thenComparing(
              source -> source.containerPosition() == null ? 0 : source.containerPosition().z())
          .thenComparing(
              source -> source.containerTypeId() == null ? "" : source.containerTypeId());

  public FoundItemSource {
    Objects.requireNonNull(sourceType, "sourceType");
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }
    inventorySlots =
        ModelValidation.immutableList(inventorySlots, "inventorySlots").stream().sorted().toList();
    equipmentSlots =
        ModelValidation.immutableList(equipmentSlots, "equipmentSlots").stream()
            .sorted(Comparator.naturalOrder())
            .toList();
    containerSlots =
        ModelValidation.immutableList(containerSlots, "containerSlots").stream().sorted().toList();
    requireUnique(inventorySlots, "inventorySlots");
    requireUnique(equipmentSlots, "equipmentSlots");
    requireUnique(containerSlots, "containerSlots");
    for (int slot : inventorySlots) {
      if (slot < 0 || slot >= InventorySnapshot.MAIN_SLOT_COUNT) {
        throw new IllegalArgumentException("inventorySlots contains an unsupported slot");
      }
    }
    containerTypeId = ModelValidation.optionalRegistryId(containerTypeId, "containerTypeId");
    if (distance != null) {
      ModelValidation.nonNegative(distance, "distance");
    }
    validateLocation(
        sourceType,
        inventorySlots,
        equipmentSlots,
        containerSlots,
        containerPosition,
        containerTypeId,
        distance);
  }

  /** Returns immutable deterministic source locations and rejects duplicate source identities. */
  public static List<FoundItemSource> normalized(List<FoundItemSource> sources, String name) {
    List<FoundItemSource> normalized =
        ModelValidation.immutableList(sources, name).stream().sorted(ORDER).toList();
    Set<String> keys = new HashSet<>();
    for (FoundItemSource source : normalized) {
      String key =
          source.sourceType()
              + ":"
              + Objects.toString(source.containerPosition(), "")
              + ":"
              + Objects.toString(source.containerTypeId(), "");
      if (!keys.add(key)) {
        throw new IllegalArgumentException(name + " contains a duplicate source location");
      }
    }
    return normalized;
  }

  private static void validateLocation(
      FoundItemSourceType sourceType,
      List<Integer> inventorySlots,
      List<EquipmentPosition> equipmentSlots,
      List<String> containerSlots,
      BlockPosition containerPosition,
      String containerTypeId,
      Double distance) {
    switch (sourceType) {
      case PLAYER_INVENTORY -> {
        require(!inventorySlots.isEmpty(), "player inventory source must include slots");
        require(equipmentSlots.isEmpty(), "player inventory source cannot include equipment slots");
        require(containerSlots.isEmpty(), "player inventory source cannot include container slots");
        requireNoContainer(containerPosition, containerTypeId, distance);
      }
      case PLAYER_EQUIPMENT -> {
        require(inventorySlots.isEmpty(), "player equipment source cannot include inventory slots");
        require(!equipmentSlots.isEmpty(), "player equipment source must include slots");
        require(containerSlots.isEmpty(), "player equipment source cannot include container slots");
        requireNoContainer(containerPosition, containerTypeId, distance);
      }
      case NEARBY_CONTAINER -> {
        require(inventorySlots.isEmpty(), "container source cannot include inventory slots");
        require(equipmentSlots.isEmpty(), "container source cannot include equipment slots");
        require(!containerSlots.isEmpty(), "container source must include slots");
        Objects.requireNonNull(containerPosition, "containerPosition");
        Objects.requireNonNull(containerTypeId, "containerTypeId");
        Objects.requireNonNull(distance, "distance");
      }
      default -> throw new IllegalArgumentException("unsupported item source type " + sourceType);
    }
  }

  private static void requireNoContainer(
      BlockPosition containerPosition, String containerTypeId, Double distance) {
    require(containerPosition == null, "player source cannot include a container position");
    require(containerTypeId == null, "player source cannot include a container type");
    require(distance == null, "player source cannot include container distance");
  }

  private static <T> void requireUnique(List<T> values, String name) {
    Set<T> unique = new HashSet<>();
    for (T value : values) {
      if (!unique.add(value)) {
        throw new IllegalArgumentException(name + " contains duplicate " + value);
      }
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
