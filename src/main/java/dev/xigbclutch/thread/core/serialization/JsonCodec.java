package dev.xigbclutch.thread.core.serialization;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import dev.xigbclutch.thread.core.error.ToolError;
import dev.xigbclutch.thread.core.error.ToolErrorCode;
import dev.xigbclutch.thread.core.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit schema and Gson conversion for one Thread contract type. */
public final class JsonCodec<T> {
  private final Gson gson;
  private final Class<T> type;
  private final JsonSchema schema;

  public JsonCodec(Gson gson, Class<T> type, JsonSchema schema) {
    this.gson = Objects.requireNonNull(gson, "gson");
    this.type = Objects.requireNonNull(type, "type");
    this.schema = Objects.requireNonNull(schema, "schema");
  }

  /** Creates a codec using Thread's standard Gson configuration. */
  public static <T> JsonCodec<T> of(Class<T> type, JsonSchema schema) {
    return new JsonCodec<>(ThreadJson.create(), type, schema);
  }

  /** Returns the explicit schema associated with this codec. */
  public JsonSchema schema() {
    return schema;
  }

  /** Validates and decodes client-provided JSON. */
  public ToolResult<T> decode(JsonElement input) {
    JsonElement normalized = input == null ? com.google.gson.JsonNull.INSTANCE : input;
    List<SchemaViolation> violations = schema.validate(normalized);
    if (!violations.isEmpty()) {
      return ToolResult.failure(validationError(violations));
    }
    try {
      T decoded = gson.fromJson(normalized, type);
      if (decoded == null) {
        return ToolResult.failure(
            ToolError.of(ToolErrorCode.INVALID_INPUT, "Input decoded to null.", false));
      }
      return ToolResult.success(decoded);
    } catch (RuntimeException exception) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.INVALID_INPUT,
              "Input could not be decoded.",
              false,
              Map.of("reason", rootMessage(exception))));
    }
  }

  /** Encodes a value and verifies that the generated JSON still matches its contract schema. */
  public ToolResult<JsonElement> encode(T value) {
    Objects.requireNonNull(value, "value");
    try {
      JsonElement encoded = gson.toJsonTree(value, type);
      List<SchemaViolation> violations = schema.validate(encoded);
      if (!violations.isEmpty()) {
        SchemaViolation first = violations.getFirst();
        return ToolResult.failure(
            new ToolError(
                ToolErrorCode.INTERNAL_ERROR,
                "Tool output does not match its declared schema.",
                false,
                Map.of("path", first.path(), "reason", first.message())));
      }
      return ToolResult.success(encoded);
    } catch (RuntimeException exception) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.INTERNAL_ERROR,
              "Tool output could not be serialized.",
              false,
              Map.of("reason", rootMessage(exception))));
    }
  }

  private static ToolError validationError(List<SchemaViolation> violations) {
    SchemaViolation first = violations.getFirst();
    return new ToolError(
        ToolErrorCode.INVALID_INPUT,
        "Input does not match the declared schema.",
        false,
        Map.of(
            "path",
            first.path(),
            "reason",
            first.message(),
            "violationCount",
            Integer.toString(violations.size())));
  }

  private static String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    String message = current.getMessage();
    return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
  }
}
