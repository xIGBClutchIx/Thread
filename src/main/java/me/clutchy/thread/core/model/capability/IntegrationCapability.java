package me.clutchy.thread.core.model.capability;

import java.util.regex.Pattern;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Stable identity and version of one active Thread game integration. */
public record IntegrationCapability(String id, String version) {
  private static final Pattern ID_FORMAT = Pattern.compile("[a-z][a-z0-9_-]*");

  public IntegrationCapability {
    id = ModelValidation.nonBlank(id, "id");
    if (!ID_FORMAT.matcher(id).matches()) {
      throw new IllegalArgumentException("id must be a canonical integration ID");
    }
    version = ModelValidation.nonBlank(version, "version");
  }
}
