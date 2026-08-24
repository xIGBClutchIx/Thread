package dev.xigbclutch.thread.core.model;

import java.util.Map;
import java.util.Objects;

/** Snapshot of the block currently targeted by the local player's camera raycast. */
public record BlockInfo(
    String blockId, BlockPosition position, Map<String, String> properties, double distance) {
  public BlockInfo {
    blockId = ModelValidation.registryId(blockId, "blockId");
    Objects.requireNonNull(position, "position");
    properties = ModelValidation.immutableSortedMap(properties, "properties");
    ModelValidation.nonNegative(distance, "distance");
  }
}
