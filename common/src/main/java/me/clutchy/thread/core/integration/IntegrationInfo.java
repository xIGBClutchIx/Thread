package me.clutchy.thread.core.integration;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Discovery metadata for one active Thread integration. */
public record IntegrationInfo(
    IntegrationId id, String version, String description, Map<String, String> metadata) {
  private static final int MAX_METADATA_ENTRIES = 32;
  private static final int MAX_METADATA_VALUE_CODE_POINTS = 256;
  private static final Pattern METADATA_KEY =
      Pattern.compile("[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+");

  public IntegrationInfo {
    Objects.requireNonNull(id, "id");
    version = requireText(version, "version");
    description = requireText(description, "description");
    Objects.requireNonNull(metadata, "metadata");
    if (metadata.size() > MAX_METADATA_ENTRIES) {
      throw new IllegalArgumentException(
          "integration metadata must not exceed " + MAX_METADATA_ENTRIES + " entries");
    }
    TreeMap<String, String> validated = new TreeMap<>();
    metadata.forEach(
        (key, value) -> validated.put(validateMetadataKey(key), validateMetadataValue(value)));
    metadata = Collections.unmodifiableMap(validated);
  }

  static String validateMetadataKey(String key) {
    String validated = requireText(key, "metadata key");
    if (!METADATA_KEY.matcher(validated).matches()) {
      throw new IllegalArgumentException("invalid integration metadata key: " + validated);
    }
    return validated;
  }

  static String validateMetadataValue(String value) {
    String validated = requireText(value, "metadata value");
    if (validated.codePointCount(0, validated.length()) > MAX_METADATA_VALUE_CODE_POINTS) {
      throw new IllegalArgumentException(
          "integration metadata value must not exceed "
              + MAX_METADATA_VALUE_CODE_POINTS
              + " characters");
    }
    return validated;
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return normalized;
  }
}
