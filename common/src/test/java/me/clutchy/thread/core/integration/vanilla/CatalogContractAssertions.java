package me.clutchy.thread.core.integration.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.clutchy.thread.core.tool.ToolDescriptor;

/** Structural assertions for the built-in model-facing MCP catalog. */
final class CatalogContractAssertions {
  private static final Set<String> OPTIONAL_WITHOUT_DEFAULT = Set.of("search");
  private static final List<String> FORBIDDEN_IMPLEMENTATION_NAMES =
      List.of(
          "net.fabricmc",
          "net.neoforged",
          "net.minecraftforge",
          "net.minecraft.class_",
          "fabric",
          "neoforge",
          "forge");

  private CatalogContractAssertions() {}

  static void assertCatalog(
      List<ToolDescriptor> descriptors,
      JsonObject response,
      List<String> expectedIds,
      String expectedFingerprint) {
    assertEquals(expectedIds, descriptors.stream().map(tool -> tool.id().value()).toList());
    assertEquals(expectedIds.size(), new HashSet<>(expectedIds).size(), "duplicate expected ID");

    JsonObject result = response.getAsJsonObject("result");
    assertNotNull(result, "tools/list result");
    JsonArray tools = result.getAsJsonArray("tools");
    assertNotNull(tools, "tools/list catalog");
    assertEquals(expectedIds.size(), tools.size());

    for (int index = 0; index < descriptors.size(); index++) {
      ToolDescriptor descriptor = descriptors.get(index);
      JsonObject tool = tools.get(index).getAsJsonObject();
      String path = "tools[" + index + "]";
      assertEquals(descriptor.id().value(), tool.get("name").getAsString(), path);
      assertEquals(descriptor.description(), tool.get("description").getAsString(), path);
      assertFalse(descriptor.description().isBlank(), path);
      assertTrue(
          descriptor.description().contains("Available in the main menu")
              || descriptor.description().contains("Requires a supported single-player world"),
          path + " must state session availability");

      JsonObject inputSchema = tool.getAsJsonObject("inputSchema");
      JsonObject outputSchema = tool.getAsJsonObject("outputSchema");
      assertEquals(descriptor.inputSchema().document(), inputSchema, path + ".inputSchema");
      assertEquals(descriptor.outputSchema().document(), outputSchema, path + ".outputSchema");
      validateSchema(inputSchema, path + ".inputSchema");
      validateSchema(outputSchema, path + ".outputSchema");
      validateInputDefaultsAndBounds(inputSchema, path + ".inputSchema");

      JsonObject annotations = tool.getAsJsonObject("annotations");
      assertTrue(annotations.get("readOnlyHint").getAsBoolean(), path);
      assertFalse(annotations.get("destructiveHint").getAsBoolean(), path);
      assertTrue(annotations.get("idempotentHint").getAsBoolean(), path);
      assertFalse(annotations.get("openWorldHint").getAsBoolean(), path);
      assertEquals(
          descriptor.capabilities().availability().name(),
          tool.getAsJsonObject("_meta").get("me.clutchy.thread/availability").getAsString(),
          path);

      String exposedContract = tool.toString().toLowerCase(java.util.Locale.ROOT);
      for (String forbidden : FORBIDDEN_IMPLEMENTATION_NAMES) {
        assertFalse(exposedContract.contains(forbidden), path + " leaks " + forbidden);
      }
    }

    assertEquals(expectedFingerprint, semanticFingerprint(tools));
  }

  static String semanticFingerprint(JsonArray tools) {
    JsonArray normalized = tools.deepCopy();
    for (JsonElement element : normalized) {
      JsonObject tool = element.getAsJsonObject();
      tool.remove("description");
      stripSchemaDescriptions(tool.getAsJsonObject("inputSchema"));
      stripSchemaDescriptions(tool.getAsJsonObject("outputSchema"));
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(normalized.toString().getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError("SHA-256 must be available", exception);
    }
  }

  private static void validateInputDefaultsAndBounds(JsonObject schema, String path) {
    JsonObject properties = schema.getAsJsonObject("properties");
    Set<String> required = strings(schema.getAsJsonArray("required"));
    for (String name : properties.keySet()) {
      JsonObject property = properties.getAsJsonObject(name);
      if (!required.contains(name) && !OPTIONAL_WITHOUT_DEFAULT.contains(name)) {
        assertTrue(property.has("default"), path + ".properties." + name + " needs a default");
      }
      if (isNumeric(property)) {
        assertTrue(
            property.has("minimum") || property.has("exclusiveMinimum"),
            path + ".properties." + name + " needs a lower bound");
        assertTrue(property.has("maximum"), path + ".properties." + name + " needs an upper bound");
      }
    }
  }

  private static void validateSchema(JsonObject schema, String path) {
    JsonElement type = schema.get("type");
    assertNotNull(type, path + " needs a type");
    if (schema.has("description")) {
      assertTrue(schema.get("description").isJsonPrimitive(), path + ".description");
      assertFalse(schema.get("description").getAsString().isBlank(), path + ".description");
    }
    if (schema.has("enum")) {
      JsonArray values = schema.getAsJsonArray("enum");
      assertFalse(values.isEmpty(), path + ".enum");
      assertEquals(values.size(), new HashSet<>(values.asList()).size(), path + ".enum duplicates");
    }
    if (schema.has("minimum") && schema.has("maximum")) {
      assertTrue(
          schema.get("minimum").getAsBigDecimal().compareTo(schema.get("maximum").getAsBigDecimal())
              <= 0,
          path + " has inverted numeric bounds");
    }
    if (schema.has("exclusiveMinimum") && schema.has("maximum")) {
      assertTrue(
          schema
                  .get("exclusiveMinimum")
                  .getAsBigDecimal()
                  .compareTo(schema.get("maximum").getAsBigDecimal())
              < 0,
          path + " has inverted numeric bounds");
    }
    if (schema.has("minItems") && schema.has("maxItems")) {
      assertTrue(
          schema.get("minItems").getAsInt() <= schema.get("maxItems").getAsInt(),
          path + " has inverted array bounds");
    }
    if (schema.has("minLength") && schema.has("maxLength")) {
      assertTrue(
          schema.get("minLength").getAsInt() <= schema.get("maxLength").getAsInt(),
          path + " has inverted string bounds");
    }

    if (isObject(type)) {
      JsonObject properties = schema.getAsJsonObject("properties");
      JsonArray required = schema.getAsJsonArray("required");
      if (properties == null || required == null) {
        assertTrue(
            schema.has("additionalProperties") && schema.get("additionalProperties").getAsBoolean(),
            path + " must be a declared open object");
        return;
      }
      Set<String> requiredNames = strings(required);
      assertEquals(required.size(), requiredNames.size(), path + ".required duplicates");
      assertTrue(
          properties.keySet().containsAll(requiredNames), path + ".required unknown property");
      for (String name : properties.keySet()) {
        validateSchema(properties.getAsJsonObject(name), path + ".properties." + name);
      }
    }
    if (isArray(type)) {
      assertNotNull(schema.getAsJsonObject("items"), path + ".items");
      validateSchema(schema.getAsJsonObject("items"), path + ".items");
    }
  }

  private static boolean isObject(JsonElement type) {
    return hasType(type, "object");
  }

  private static boolean isArray(JsonElement type) {
    return hasType(type, "array");
  }

  private static boolean isNumeric(JsonObject schema) {
    JsonElement type = schema.get("type");
    return hasType(type, "integer") || hasType(type, "number");
  }

  private static boolean hasType(JsonElement type, String expected) {
    if (type.isJsonPrimitive()) {
      return type.getAsString().equals(expected);
    }
    return type.getAsJsonArray().asList().stream()
        .map(JsonElement::getAsString)
        .anyMatch(expected::equals);
  }

  private static Set<String> strings(JsonArray values) {
    Set<String> result = new HashSet<>();
    for (JsonElement value : values) {
      result.add(value.getAsString());
    }
    return result;
  }

  private static void stripSchemaDescriptions(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return;
    }
    if (element.isJsonArray()) {
      element.getAsJsonArray().forEach(CatalogContractAssertions::stripSchemaDescriptions);
      return;
    }
    if (!element.isJsonObject()) {
      return;
    }
    JsonObject object = element.getAsJsonObject();
    if (object.has("type")) {
      object.remove("description");
    }
    object.entrySet().forEach(entry -> stripSchemaDescriptions(entry.getValue()));
  }
}
