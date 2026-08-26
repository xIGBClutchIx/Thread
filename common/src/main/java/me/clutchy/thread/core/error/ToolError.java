package me.clutchy.thread.core.error;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Structured failure returned from a tool-facing operation.
 *
 * <p>The code is stable and machine-readable. The message is intended for people, while details
 * contain small non-sensitive diagnostic fields that transports may serialize directly.
 *
 * @param code stable error category
 * @param message human-readable explanation
 * @param retryable whether retrying after game state changes may succeed
 * @param details deterministic diagnostic fields; never complete player or world state
 */
public record ToolError(
    ToolErrorCode code, String message, boolean retryable, Map<String, String> details) {
  public ToolError {
    Objects.requireNonNull(code, "code");
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
    Objects.requireNonNull(details, "details");
    TreeMap<String, String> copy = new TreeMap<>();
    details.forEach(
        (key, value) -> {
          if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("detail keys must not be blank");
          }
          copy.put(key, Objects.requireNonNull(value, "detail value"));
        });
    details = Collections.unmodifiableMap(copy);
  }

  /** Creates an error without diagnostic details. */
  public static ToolError of(ToolErrorCode code, String message, boolean retryable) {
    return new ToolError(code, message, retryable, Map.of());
  }
}
