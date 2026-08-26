package me.clutchy.thread.core.model.world;

import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Compact identity and occupancy summary for one nearby loaded container. */
public record NearbyContainerSummary(
    String blockId,
    String containerTypeId,
    String displayName,
    BlockPosition position,
    double distance,
    int slotCount,
    Integer usedSlotCount,
    List<BlockEntityItemInfo> itemSummary,
    boolean itemSummaryTruncated) {
  /** Maximum number of representative non-empty slots included in nearby search results. */
  public static final int MAX_SUMMARY_ITEMS = 4;

  public NearbyContainerSummary {
    blockId = ModelValidation.registryId(blockId, "blockId");
    containerTypeId = ModelValidation.registryId(containerTypeId, "containerTypeId");
    displayName = ModelValidation.boundedNonBlank(displayName, "displayName", 256);
    Objects.requireNonNull(position, "position");
    ModelValidation.nonNegative(distance, "distance");
    if (slotCount < 0) {
      throw new IllegalArgumentException("slotCount must not be negative");
    }
    if (usedSlotCount != null && (usedSlotCount < 0 || usedSlotCount > slotCount)) {
      throw new IllegalArgumentException("usedSlotCount must be between zero and slotCount");
    }
    itemSummary = ModelValidation.immutableList(itemSummary, "itemSummary");
    if (itemSummary.size() > MAX_SUMMARY_ITEMS) {
      throw new IllegalArgumentException(
          "itemSummary must not exceed " + MAX_SUMMARY_ITEMS + " entries");
    }
    if (usedSlotCount != null && itemSummary.size() > usedSlotCount) {
      throw new IllegalArgumentException("itemSummary must not exceed usedSlotCount");
    }
    if (itemSummaryTruncated && usedSlotCount != null && itemSummary.size() >= usedSlotCount) {
      throw new IllegalArgumentException("truncated itemSummary must omit at least one used slot");
    }
  }
}
