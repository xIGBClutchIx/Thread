package me.clutchy.thread.core.context;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable identifier for a small static or semi-static context value. */
public record ContextId(String value) implements Comparable<ContextId> {
  private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+");

  public ContextId {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid context ID: " + value);
    }
  }

  /** Creates a validated context identifier. */
  public static ContextId of(String value) {
    return new ContextId(value);
  }

  @Override
  public int compareTo(ContextId other) {
    return value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value;
  }
}
