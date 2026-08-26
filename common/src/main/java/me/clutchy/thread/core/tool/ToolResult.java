package me.clutchy.thread.core.tool;

import java.util.Objects;
import me.clutchy.thread.core.error.ToolError;

/**
 * Success-or-failure result returned by Thread core operations.
 *
 * <p>Exactly one of {@code value} or {@code error} is present. Successful null values are not
 * permitted; tools that conceptually return no content should use an explicit empty DTO.
 *
 * @param value successful value, or {@code null} on failure
 * @param error structured error, or {@code null} on success
 */
public record ToolResult<T>(T value, ToolError error) {
  public ToolResult {
    if ((value == null) == (error == null)) {
      throw new IllegalArgumentException("exactly one of value or error must be present");
    }
  }

  /** Creates a successful result. */
  public static <T> ToolResult<T> success(T value) {
    return new ToolResult<>(Objects.requireNonNull(value, "value"), null);
  }

  /** Creates a failed result. */
  public static <T> ToolResult<T> failure(ToolError error) {
    return new ToolResult<>(null, Objects.requireNonNull(error, "error"));
  }

  /** Returns whether this result contains a successful value. */
  public boolean successful() {
    return error == null;
  }
}
