package dev.xigbclutch.thread.core.tool;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable dot-separated identifier exposed to tool clients, such as {@code minecraft.get_status}.
 */
public record ToolId(String value) implements Comparable<ToolId> {
  private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+");

  public ToolId {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid tool ID: " + value);
    }
  }

  /** Creates a validated tool identifier. */
  public static ToolId of(String value) {
    return new ToolId(value);
  }

  @Override
  public int compareTo(ToolId other) {
    return value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value;
  }
}
