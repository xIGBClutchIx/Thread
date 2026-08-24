package dev.xigbclutch.thread.core.integration;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable lower-case identifier for a game integration. */
public record IntegrationId(String value) implements Comparable<IntegrationId> {
  private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_-]*");

  public IntegrationId {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid integration ID: " + value);
    }
  }

  /** Creates a validated integration identifier. */
  public static IntegrationId of(String value) {
    return new IntegrationId(value);
  }

  @Override
  public int compareTo(IntegrationId other) {
    return value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value;
  }
}
