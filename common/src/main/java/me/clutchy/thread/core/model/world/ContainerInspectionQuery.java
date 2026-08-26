package me.clutchy.thread.core.model.world;

import java.util.Objects;

/** Identifies one nearby loaded block position for safe container inspection. */
public record ContainerInspectionQuery(BlockPosition position) {
  public ContainerInspectionQuery {
    Objects.requireNonNull(position, "position");
  }
}
