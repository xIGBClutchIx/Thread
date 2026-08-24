package dev.xigbclutch.thread.core.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xigbclutch.thread.core.error.ToolErrorCode;
import dev.xigbclutch.thread.core.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonSchemaTest {
  @Test
  void validatesTheExplicitSchemaSubsetInStableTraversalOrder() {
    JsonSchema schema =
        JsonSchema.parse(
            """
            {
              "type": "object",
              "properties": {
                "count": {"type": "integer", "minimum": 1, "maximum": 3},
                "name": {"type": "string", "minLength": 2},
                "tags": {
                  "type": "array",
                  "maxItems": 2,
                  "items": {"type": "string", "pattern": "^[a-z]+$"}
                }
              },
              "required": ["name", "count"],
              "additionalProperties": false
            }
            """);

    List<SchemaViolation> violations =
        schema.validate(
            JsonParser.parseString(
                "{\"count\":4,\"extra\":true,\"tags\":[\"ok\",\"BAD\",\"more\"]}"));

    assertEquals(
        List.of("$.name", "$.count", "$.extra", "$.tags", "$.tags[1]"),
        violations.stream().map(SchemaViolation::path).toList());
  }

  @Test
  void supportsNullableFieldsThroughATypeUnion() {
    JsonSchema schema =
        JsonSchema.parse(
            """
            {
              "type": "object",
              "properties": {"value": {"type": ["string", "null"]}},
              "required": ["value"],
              "additionalProperties": false
            }
            """);

    assertTrue(schema.validate(JsonParser.parseString("{\"value\":null}")).isEmpty());
    assertTrue(schema.validate(JsonParser.parseString("{\"value\":\"ok\"}")).isEmpty());
  }

  @Test
  void schemaDocumentsAreDefensivelyCopied() {
    JsonObject source = JsonParser.parseString("{\"type\":\"object\"}").getAsJsonObject();
    JsonSchema schema = JsonSchema.of(source);
    source.addProperty("type", "string");
    JsonObject exposed = schema.document();
    exposed.addProperty("type", "array");

    assertEquals("object", schema.document().get("type").getAsString());
  }

  @Test
  void codecReportsRecordConstructorValidationAsInvalidInput() {
    JsonCodec<PositiveCount> codec =
        JsonCodec.of(
            PositiveCount.class,
            JsonSchema.parse(
                """
                {
                  "type": "object",
                  "properties": {"count": {"type": "integer", "minimum": 0}},
                  "required": ["count"],
                  "additionalProperties": false
                }
                """));

    ToolResult<PositiveCount> result = codec.decode(JsonParser.parseString("{\"count\":0}"));

    assertFalse(result.successful());
    assertEquals(ToolErrorCode.INVALID_INPUT, result.error().code());
    assertEquals("count must be positive", result.error().details().get("reason"));
  }

  @Test
  void codecDetectsOutputThatDriftsFromItsSchema() {
    JsonCodec<PositiveCount> codec =
        JsonCodec.of(
            PositiveCount.class,
            JsonSchema.parse(
                """
                {
                  "type": "object",
                  "properties": {"different": {"type": "integer"}},
                  "required": ["different"],
                  "additionalProperties": false
                }
                """));

    ToolResult<com.google.gson.JsonElement> result = codec.encode(new PositiveCount(1));

    assertEquals(ToolErrorCode.INTERNAL_ERROR, result.error().code());
    assertEquals("$.different", result.error().details().get("path"));
  }

  @Test
  void rejectsMalformedSchemaDocuments() {
    assertThrows(IllegalArgumentException.class, () -> JsonSchema.parse("[]"));
    assertThrows(IllegalArgumentException.class, () -> JsonSchema.parse("{"));
    assertThrows(
        IllegalArgumentException.class, () -> JsonSchema.parse("{\"type\":\"unsupported\"}"));
    assertThrows(
        IllegalArgumentException.class, () -> JsonSchema.parse("{\"additionalProperties\":{}}"));
  }

  private record PositiveCount(int count) {
    private PositiveCount {
      if (count <= 0) {
        throw new IllegalArgumentException("count must be positive");
      }
    }
  }
}
