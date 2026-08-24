package dev.xigbclutch.thread.core.tool;

import dev.xigbclutch.thread.core.serialization.JsonSchema;
import java.util.Objects;

/** Stable discovery metadata for one registered tool. */
public record ToolDescriptor(
    ToolId id,
    String description,
    JsonSchema inputSchema,
    JsonSchema outputSchema,
    ToolCapabilities capabilities) {
  public ToolDescriptor {
    Objects.requireNonNull(id, "id");
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("description must not be blank");
    }
    Objects.requireNonNull(inputSchema, "inputSchema");
    Objects.requireNonNull(outputSchema, "outputSchema");
    Objects.requireNonNull(capabilities, "capabilities");
  }
}
