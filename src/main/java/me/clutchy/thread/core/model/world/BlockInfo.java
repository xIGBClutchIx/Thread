package me.clutchy.thread.core.model.world;

import java.util.Map;
import java.util.Objects;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Snapshot of the block currently targeted by the local player's camera raycast. */
public record BlockInfo(
    String blockId,
    String displayName,
    BlockPosition position,
    Map<String, String> properties,
    double distance,
    boolean blockEntityPresent,
    BlockEntityInfo blockEntity) {
  public BlockInfo {
    blockId = ModelValidation.registryId(blockId, "blockId");
    displayName = ModelValidation.boundedNonBlank(displayName, "displayName", 256);
    Objects.requireNonNull(position, "position");
    properties = ModelValidation.immutableSortedMap(properties, "properties");
    ModelValidation.nonNegative(distance, "distance");
    if (blockEntityPresent != (blockEntity != null)) {
      throw new IllegalArgumentException(
          "blockEntity must be present exactly when blockEntityPresent is true");
    }
  }
}
