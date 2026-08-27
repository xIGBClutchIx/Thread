package me.clutchy.thread.core.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Immutable JSON Schema document and validator for the explicit subset used by Thread contracts.
 *
 * <p>Thread owns its schemas rather than deriving them from Java reflection. The supported keywords
 * are {@code type}, {@code enum}, {@code properties}, {@code required}, {@code
 * additionalProperties}, {@code items}, {@code minItems}, {@code maxItems}, {@code minLength},
 * {@code maxLength}, {@code pattern}, {@code minimum}, {@code exclusiveMinimum}, {@code maximum},
 * {@code default}, {@code description}, and {@code format}.
 */
public final class JsonSchema {
  private static final Set<String> SUPPORTED_TYPES =
      Set.of("null", "object", "array", "string", "boolean", "number", "integer");

  private final JsonObject document;

  private JsonSchema(JsonObject document) {
    this.document = document.deepCopy();
    validateDefinition(this.document, "$schema");
  }

  /** Creates a schema from a JSON object, taking a defensive copy. */
  public static JsonSchema of(JsonObject document) {
    return new JsonSchema(Objects.requireNonNull(document, "document"));
  }

  /** Parses a schema from JSON text. */
  public static JsonSchema parse(String json) {
    Objects.requireNonNull(json, "json");
    try {
      JsonElement parsed = JsonParser.parseString(json);
      if (!parsed.isJsonObject()) {
        throw new IllegalArgumentException("a JSON schema must be an object");
      }
      return of(parsed.getAsJsonObject());
    } catch (JsonParseException exception) {
      throw new IllegalArgumentException("invalid JSON schema", exception);
    }
  }

  /** Returns a defensive copy suitable for discovery serialization. */
  public JsonObject document() {
    return document.deepCopy();
  }

  /** Validates a JSON value and returns violations in deterministic traversal order. */
  public List<SchemaViolation> validate(JsonElement value) {
    List<SchemaViolation> violations = new ArrayList<>();
    validateNode(document, value == null ? JsonNull.INSTANCE : value, "$", violations);
    return List.copyOf(violations);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsonSchema schema && document.equals(schema.document);
  }

  @Override
  public int hashCode() {
    return document.hashCode();
  }

  @Override
  public String toString() {
    return document.toString();
  }

  private static void validateDefinition(JsonObject schema, String path) {
    validateTypeDefinition(schema.get("type"), path + ".type");
    requireArray(schema, "enum");
    requireBoolean(schema, "additionalProperties");
    requireNonNegativeInteger(schema, "minItems");
    requireNonNegativeInteger(schema, "maxItems");
    requireNonNegativeInteger(schema, "minLength");
    requireNonNegativeInteger(schema, "maxLength");
    requireNumber(schema, "minimum");
    requireNumber(schema, "exclusiveMinimum");
    requireNumber(schema, "maximum");
    requireString(schema, "description");
    requireString(schema, "format");

    JsonObject properties = requireObject(schema, "properties");
    if (properties != null) {
      for (String name : properties.keySet()) {
        JsonElement property = properties.get(name);
        if (!property.isJsonObject()) {
          throw new IllegalArgumentException(path + ".properties." + name + " must be an object");
        }
        validateDefinition(property.getAsJsonObject(), path + ".properties." + name);
      }
    }

    JsonArray required = requireArray(schema, "required");
    if (required != null) {
      for (JsonElement name : required) {
        if (!name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) {
          throw new IllegalArgumentException(path + ".required entries must be strings");
        }
      }
    }

    JsonElement items = schema.get("items");
    if (items != null) {
      if (!items.isJsonObject()) {
        throw new IllegalArgumentException(path + ".items must be an object");
      }
      validateDefinition(items.getAsJsonObject(), path + ".items");
    }

    if (schema.has("pattern")) {
      JsonElement pattern = schema.get("pattern");
      if (!pattern.isJsonPrimitive() || !pattern.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException(path + ".pattern must be a string");
      }
      try {
        Pattern.compile(pattern.getAsString());
      } catch (PatternSyntaxException exception) {
        throw new IllegalArgumentException(path + ".pattern is invalid", exception);
      }
    }

    JsonElement defaultValue = schema.get("default");
    if (defaultValue != null) {
      List<SchemaViolation> violations = new ArrayList<>();
      validateNode(schema, defaultValue, path + ".default", violations);
      if (!violations.isEmpty()) {
        throw new IllegalArgumentException(
            path + ".default does not satisfy its schema: " + violations.getFirst().message());
      }
    }
  }

  private static void validateTypeDefinition(JsonElement type, String path) {
    if (type == null) {
      return;
    }
    if (type.isJsonPrimitive() && type.getAsJsonPrimitive().isString()) {
      requireSupportedType(type.getAsString(), path);
      return;
    }
    if (type.isJsonArray() && !type.getAsJsonArray().isEmpty()) {
      for (JsonElement candidate : type.getAsJsonArray()) {
        if (!candidate.isJsonPrimitive() || !candidate.getAsJsonPrimitive().isString()) {
          throw new IllegalArgumentException(path + " entries must be strings");
        }
        requireSupportedType(candidate.getAsString(), path);
      }
      return;
    }
    throw new IllegalArgumentException(path + " must be a string or non-empty array of strings");
  }

  private static void requireSupportedType(String type, String path) {
    if (!SUPPORTED_TYPES.contains(type)) {
      throw new IllegalArgumentException(path + " contains unsupported type: " + type);
    }
  }

  private static JsonObject requireObject(JsonObject schema, String name) {
    JsonElement value = schema.get(name);
    if (value == null) {
      return null;
    }
    if (!value.isJsonObject()) {
      throw new IllegalArgumentException(name + " must be an object");
    }
    return value.getAsJsonObject();
  }

  private static JsonArray requireArray(JsonObject schema, String name) {
    JsonElement value = schema.get(name);
    if (value == null) {
      return null;
    }
    if (!value.isJsonArray()) {
      throw new IllegalArgumentException(name + " must be an array");
    }
    return value.getAsJsonArray();
  }

  private static void requireBoolean(JsonObject schema, String name) {
    JsonElement value = schema.get(name);
    if (value != null && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())) {
      throw new IllegalArgumentException(name + " must be a boolean");
    }
  }

  private static void requireNonNegativeInteger(JsonObject schema, String name) {
    JsonElement value = schema.get(name);
    if (value == null) {
      return;
    }
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException(name + " must be a non-negative integer");
    }
    BigDecimal number = value.getAsBigDecimal();
    if (number.signum() < 0 || number.stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException(name + " must be a non-negative integer");
    }
  }

  private static void requireNumber(JsonObject schema, String name) {
    JsonElement value = schema.get(name);
    if (value != null && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())) {
      throw new IllegalArgumentException(name + " must be a number");
    }
  }

  private static void requireString(JsonObject schema, String name) {
    JsonElement value = schema.get(name);
    if (value != null && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())) {
      throw new IllegalArgumentException(name + " must be a string");
    }
  }

  private static void validateNode(
      JsonObject schema, JsonElement value, String path, List<SchemaViolation> violations) {
    if (!matchesDeclaredType(schema.get("type"), value)) {
      violations.add(new SchemaViolation(path, "expected " + describeTypes(schema.get("type"))));
      return;
    }

    JsonArray allowedValues = arrayKeyword(schema, "enum");
    if (allowedValues != null && !contains(allowedValues, value)) {
      violations.add(new SchemaViolation(path, "value is not in the allowed enum"));
    }

    if (value.isJsonObject()) {
      validateObject(schema, value.getAsJsonObject(), path, violations);
    } else if (value.isJsonArray()) {
      validateArray(schema, value.getAsJsonArray(), path, violations);
    } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
      validateString(schema, value.getAsString(), path, violations);
    } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
      validateNumber(schema, value.getAsBigDecimal(), path, violations);
    }
  }

  private static void validateObject(
      JsonObject schema, JsonObject value, String path, List<SchemaViolation> violations) {
    JsonObject properties = objectKeyword(schema, "properties");
    JsonArray required = arrayKeyword(schema, "required");
    if (required != null) {
      for (JsonElement requiredName : required) {
        String name = requiredName.getAsString();
        if (!value.has(name)) {
          violations.add(
              new SchemaViolation(childPath(path, name), "required property is missing"));
        }
      }
    }

    Set<String> knownProperties =
        properties == null ? Set.of() : new HashSet<>(properties.keySet());
    boolean allowAdditional =
        !schema.has("additionalProperties") || schema.get("additionalProperties").getAsBoolean();
    for (String name : value.keySet().stream().sorted().toList()) {
      if (properties != null && properties.has(name)) {
        JsonElement propertySchema = properties.get(name);
        if (!propertySchema.isJsonObject()) {
          throw new IllegalStateException("property schema must be an object: " + name);
        }
        validateNode(
            propertySchema.getAsJsonObject(), value.get(name), childPath(path, name), violations);
      } else if (!allowAdditional && !knownProperties.contains(name)) {
        violations.add(
            new SchemaViolation(childPath(path, name), "additional property is not allowed"));
      }
    }
  }

  private static void validateArray(
      JsonObject schema, JsonArray value, String path, List<SchemaViolation> violations) {
    Integer minItems = integerKeyword(schema, "minItems");
    Integer maxItems = integerKeyword(schema, "maxItems");
    if (minItems != null && value.size() < minItems) {
      violations.add(new SchemaViolation(path, "array has fewer than " + minItems + " items"));
    }
    if (maxItems != null && value.size() > maxItems) {
      violations.add(new SchemaViolation(path, "array has more than " + maxItems + " items"));
    }
    JsonElement itemSchema = schema.get("items");
    if (itemSchema != null) {
      if (!itemSchema.isJsonObject()) {
        throw new IllegalStateException("items schema must be an object");
      }
      for (int index = 0; index < value.size(); index++) {
        validateNode(
            itemSchema.getAsJsonObject(), value.get(index), path + "[" + index + "]", violations);
      }
    }
  }

  private static void validateString(
      JsonObject schema, String value, String path, List<SchemaViolation> violations) {
    Integer minLength = integerKeyword(schema, "minLength");
    Integer maxLength = integerKeyword(schema, "maxLength");
    int length = value.codePointCount(0, value.length());
    if (minLength != null && length < minLength) {
      violations.add(new SchemaViolation(path, "string is shorter than " + minLength));
    }
    if (maxLength != null && length > maxLength) {
      violations.add(new SchemaViolation(path, "string is longer than " + maxLength));
    }
    if (schema.has("pattern")) {
      try {
        if (!Pattern.compile(schema.get("pattern").getAsString()).matcher(value).find()) {
          violations.add(new SchemaViolation(path, "string does not match the required pattern"));
        }
      } catch (PatternSyntaxException exception) {
        throw new IllegalStateException("schema contains an invalid pattern", exception);
      }
    }
  }

  private static void validateNumber(
      JsonObject schema, BigDecimal value, String path, List<SchemaViolation> violations) {
    if (schema.has("minimum") && value.compareTo(schema.get("minimum").getAsBigDecimal()) < 0) {
      violations.add(new SchemaViolation(path, "number is below the minimum"));
    }
    if (schema.has("exclusiveMinimum")
        && value.compareTo(schema.get("exclusiveMinimum").getAsBigDecimal()) <= 0) {
      violations.add(new SchemaViolation(path, "number is not above the exclusive minimum"));
    }
    if (schema.has("maximum") && value.compareTo(schema.get("maximum").getAsBigDecimal()) > 0) {
      violations.add(new SchemaViolation(path, "number is above the maximum"));
    }
  }

  private static boolean matchesDeclaredType(JsonElement declaration, JsonElement value) {
    if (declaration == null) {
      return true;
    }
    if (declaration.isJsonPrimitive()) {
      return matchesType(declaration.getAsString(), value);
    }
    if (declaration.isJsonArray()) {
      for (JsonElement candidate : declaration.getAsJsonArray()) {
        if (matchesType(candidate.getAsString(), value)) {
          return true;
        }
      }
      return false;
    }
    throw new IllegalStateException("schema type must be a string or array of strings");
  }

  private static boolean matchesType(String type, JsonElement value) {
    return switch (type) {
      case "null" -> value.isJsonNull();
      case "object" -> value.isJsonObject();
      case "array" -> value.isJsonArray();
      case "string" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
      case "boolean" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
      case "number" -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
      case "integer" ->
          value.isJsonPrimitive()
              && value.getAsJsonPrimitive().isNumber()
              && value.getAsBigDecimal().stripTrailingZeros().scale() <= 0;
      default -> throw new IllegalStateException("unsupported schema type: " + type);
    };
  }

  private static String describeTypes(JsonElement declaration) {
    if (declaration == null) {
      return "a valid value";
    }
    if (declaration.isJsonArray()) {
      return declaration.getAsJsonArray().asList().stream()
          .map(JsonElement::getAsString)
          .sorted()
          .reduce((left, right) -> left + " or " + right)
          .orElse("a valid value");
    }
    return declaration.getAsString();
  }

  private static boolean contains(JsonArray values, JsonElement candidate) {
    for (JsonElement value : values) {
      if (value.equals(candidate)) {
        return true;
      }
    }
    return false;
  }

  private static JsonObject objectKeyword(JsonObject schema, String name) {
    JsonElement value = schema.get(name);
    if (value == null) {
      return null;
    }
    if (!value.isJsonObject()) {
      throw new IllegalStateException(name + " must be an object");
    }
    return value.getAsJsonObject();
  }

  private static JsonArray arrayKeyword(JsonObject schema, String name) {
    JsonElement value = schema.get(name);
    if (value == null) {
      return null;
    }
    if (!value.isJsonArray()) {
      throw new IllegalStateException(name + " must be an array");
    }
    return value.getAsJsonArray();
  }

  private static Integer integerKeyword(JsonObject schema, String name) {
    JsonElement value = schema.get(name);
    return value == null ? null : value.getAsInt();
  }

  private static String childPath(String path, String property) {
    return path + "." + property;
  }
}
