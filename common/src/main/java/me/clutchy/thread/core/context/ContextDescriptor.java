package me.clutchy.thread.core.context;

import java.util.Objects;
import me.clutchy.thread.core.serialization.JsonSchema;

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
