package me.clutchy.thread.core.integration;

import java.util.Objects;

/**
 * Non-sensitive startup outcome for one optional integration candidate.
 *
 * @param id stable candidate ID
 * @param status activation result
 * @param targetModId mod whose presence gates class loading
 * @param targetModVersion detected mod version, or {@code null} when unavailable
 */
public record IntegrationActivation(
    IntegrationId id,
    IntegrationActivationStatus status,
    String targetModId,
    String targetModVersion) {
  public IntegrationActivation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(status, "status");
    targetModId = requireText(targetModId, "targetModId");
    if (targetModVersion != null && targetModVersion.isBlank()) {
      throw new IllegalArgumentException("targetModVersion must not be blank");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
