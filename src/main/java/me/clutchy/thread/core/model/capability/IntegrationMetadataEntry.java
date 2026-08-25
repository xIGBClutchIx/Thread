package me.clutchy.thread.core.model.capability;

import java.util.regex.Pattern;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** One bounded integration-specific capability metadata entry. */
public record IntegrationMetadataEntry(String key, String value) {
  private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+");

  public IntegrationMetadataEntry {
    key = ModelValidation.boundedNonBlank(key, "key", 128);
    if (!KEY.matcher(key).matches()) {
      throw new IllegalArgumentException("key must be a canonical integration metadata key");
    }
    value = ModelValidation.boundedNonBlank(value, "value", 256);
  }
}
