package me.clutchy.thread.core.model.capability;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Stable identity, version, and bounded metadata for one active Thread integration. */
public record IntegrationCapability(
    String id, String version, List<IntegrationMetadataEntry> metadata) {
  private static final Pattern ID_FORMAT = Pattern.compile("[a-z][a-z0-9_-]*");

  public IntegrationCapability {
    id = ModelValidation.nonBlank(id, "id");
    if (!ID_FORMAT.matcher(id).matches()) {
      throw new IllegalArgumentException("id must be a canonical integration ID");
    }
    version = ModelValidation.boundedNonBlank(version, "version", 128);
    metadata =
        ModelValidation.immutableList(metadata, "metadata").stream()
            .sorted(Comparator.comparing(IntegrationMetadataEntry::key))
            .toList();
    if (metadata.size() > 32) {
      throw new IllegalArgumentException("integration metadata must not exceed 32 entries");
    }
    Set<String> keys = new HashSet<>();
    for (IntegrationMetadataEntry entry : metadata) {
      if (!keys.add(entry.key())) {
        throw new IllegalArgumentException(
            "integration metadata contains duplicate key " + entry.key());
      }
    }
  }
}
