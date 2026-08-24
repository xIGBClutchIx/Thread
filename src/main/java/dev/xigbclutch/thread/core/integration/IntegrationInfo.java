package dev.xigbclutch.thread.core.integration;

import java.util.Objects;

/** Discovery metadata for one active game integration. */
public record IntegrationInfo(IntegrationId id, String version, String description) {
  public IntegrationInfo {
    Objects.requireNonNull(id, "id");
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("version must not be blank");
    }
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("description must not be blank");
    }
  }
}
