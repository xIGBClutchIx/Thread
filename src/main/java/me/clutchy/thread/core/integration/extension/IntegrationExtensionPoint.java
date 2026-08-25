package me.clutchy.thread.core.integration.extension;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Typed, stable registration key for an integration contribution owned by a core or platform
 * adapter.
 *
 * @param id namespaced extension-point ID such as {@code thread.recipe_provider}
 * @param contract contribution interface accepted by this point
 */
public record IntegrationExtensionPoint<T>(String id, Class<T> contract) {
  private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+");

  public IntegrationExtensionPoint {
    Objects.requireNonNull(id, "id");
    if (!ID.matcher(id).matches()) {
      throw new IllegalArgumentException("invalid integration extension point ID: " + id);
    }
    Objects.requireNonNull(contract, "contract");
  }
}
