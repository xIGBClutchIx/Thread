package dev.xigbclutch.thread.core.context;

import dev.xigbclutch.thread.core.serialization.JsonSchema;
import java.util.Objects;

/** Discovery metadata for one registered context provider. */
public record ContextDescriptor(ContextId id, String description, JsonSchema schema) {
  public ContextDescriptor {
    Objects.requireNonNull(id, "id");
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("description must not be blank");
    }
    Objects.requireNonNull(schema, "schema");
  }
}
