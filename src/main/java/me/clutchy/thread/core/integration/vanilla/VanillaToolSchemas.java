package me.clutchy.thread.core.integration.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.clutchy.thread.core.serialization.JsonSchema;

final class VanillaToolSchemas {
  private static final String REGISTRY_ID_PATTERN = "^[a-z0-9_.-]+:[a-z0-9/._-]+$";
  private static final String TOOL_ID_PATTERN = "^[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+$";
  private static final String INTEGRATION_ID_PATTERN = "^[a-z][a-z0-9_-]*$";

  private static final JsonObject POSITION =
      object(property("x", number()), property("y", number()), property("z", number()));
  private static final JsonObject BLOCK_POSITION =
      object(property("x", integer()), property("y", integer()), property("z", integer()));
  private static final JsonObject ITEM_INFO =
      object(property("itemId", registryId()), property("displayName", string(1, 256, null)));
  private static final JsonObject ITEM_STACK =
      object(
          property("itemId", registryId()),
          property("count", integer(1, null)),
          property("maxCount", integer(1, null)),
          property("displayName", string(1, 256, null)));
  private static final JsonObject INVENTORY_SLOT =
      object(property("slot", integer(0, null)), property("stack", ITEM_STACK));
  private static final JsonObject BLOCK_INFO =
      object(
          property("blockId", registryId()),
          property("position", BLOCK_POSITION),
          property("properties", openObject()),
          property("distance", number(0.0, null)));
  private static final JsonObject ENTITY_INFO =
      object(
          property("entityType", registryId()),
          property("distance", number(0.0, null)),
          property("position", POSITION));
  private static final JsonObject RECIPE_INGREDIENT =
      object(
          property("itemIds", array(registryId())),
          property("tagIds", array(registryId())),
          property("count", integer(1, null)));
  private static final JsonObject RECIPE_INFO =
      object(
          property("recipeId", registryId()),
          property("type", registryId()),
          property("result", ITEM_STACK),
          property("ingredients", array(RECIPE_INGREDIENT)));
  private static final JsonObject INTEGRATION_CAPABILITY =
      object(
          property("id", string(1, 64, INTEGRATION_ID_PATTERN)),
          property("version", string(1, 128, null)));

  static final JsonSchema EMPTY_INPUT = schema(object());
  static final JsonSchema SESSION_STATUS =
      schema(
          object(
              property(
                  "state", enumString("MAIN_MENU", "LOADING_WORLD", "SINGLEPLAYER", "MULTIPLAYER")),
              property("worldLoaded", bool()),
              property("playerAvailable", bool()),
              property("supported", bool()),
              property(
                  "reason",
                  nullableEnumString(
                      "NO_WORLD",
                      "WORLD_LOADING",
                      "PLAYER_NOT_AVAILABLE",
                      "MULTIPLAYER_UNSUPPORTED"))));
  static final JsonSchema GAME_INFO =
      schema(
          object(
              property("minecraftVersion", string(1, 128, null)),
              property("loader", string(1, 64, null)),
              property("loaderVersion", string(1, 128, null)),
              property("threadVersion", string(1, 128, null))));
  static final JsonSchema PLAYER_STATUS =
      schema(
          object(
              property("health", number(0.0, null)),
              property("maxHealth", number(0.0, null)),
              property("food", integer(0, null)),
              property("saturation", number(0.0, null)),
              property("experienceLevel", integer(0, null)),
              property("experienceProgress", number(0.0, 1.0)),
              property("position", POSITION),
              property("dimension", registryId()),
              property("gameMode", string(1, 64, null))));
  static final JsonSchema INVENTORY =
      schema(
          object(
              property("selectedHotbarSlot", integer(0, 8)),
              property("slots", array(INVENTORY_SLOT))));
  static final JsonSchema EQUIPMENT =
      schema(
          object(
              property("mainHand", nullable(ITEM_STACK)),
              property("offHand", nullable(ITEM_STACK)),
              property("head", nullable(ITEM_STACK)),
              property("chest", nullable(ITEM_STACK)),
              property("legs", nullable(ITEM_STACK)),
              property("feet", nullable(ITEM_STACK))));
  static final JsonSchema TARGET_BLOCK = schema(BLOCK_INFO);
  static final JsonSchema NEARBY_ENTITY_QUERY =
      schema(object(property("radius", number(0.0, null)), property("limit", integer(1, null))));
  static final JsonSchema NEARBY_ENTITY_RESULT =
      schema(
          object(
              property("radius", number(0.0, null)),
              property("limit", integer(1, null)),
              property("truncated", bool()),
              property("entities", array(ENTITY_INFO))));
  static final JsonSchema RECIPE_LOOKUP_QUERY =
      schema(object(property("itemId", string(1, 256, REGISTRY_ID_PATTERN))));
  static final JsonSchema RECIPE_LOOKUP_RESULT =
      schema(object(property("itemId", registryId()), property("recipes", array(RECIPE_INFO))));
  static final JsonSchema ITEM_SEARCH_QUERY =
      schema(object(property("query", string(1, 128, null)), property("limit", integer(1, null))));
  static final JsonSchema ITEM_SEARCH_RESULT =
      schema(
          object(
              property("query", string(1, 128, null)),
              property("limit", integer(1, null)),
              property("truncated", bool()),
              property("items", array(ITEM_INFO))));
  static final JsonSchema CAPABILITIES =
      schema(
          object(
              property("threadVersion", string(1, 128, null)),
              property("readOnly", bool()),
              property("tools", array(string(1, 128, TOOL_ID_PATTERN))),
              property("integrations", array(INTEGRATION_CAPABILITY))));

  private VanillaToolSchemas() {}

  private static JsonSchema schema(JsonObject document) {
    return JsonSchema.of(document);
  }

  private static JsonObject object(Property... properties) {
    JsonObject schema = typed("object");
    JsonObject propertySchemas = new JsonObject();
    JsonArray required = new JsonArray();
    for (Property property : properties) {
      propertySchemas.add(property.name(), property.schema().deepCopy());
      required.add(property.name());
    }
    schema.add("properties", propertySchemas);
    schema.add("required", required);
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  private static JsonObject openObject() {
    JsonObject schema = typed("object");
    schema.addProperty("additionalProperties", true);
    return schema;
  }

  private static JsonObject array(JsonObject items) {
    JsonObject schema = typed("array");
    schema.add("items", items.deepCopy());
    return schema;
  }

  private static JsonObject string(int minLength, Integer maxLength, String pattern) {
    JsonObject schema = typed("string");
    schema.addProperty("minLength", minLength);
    if (maxLength != null) {
      schema.addProperty("maxLength", maxLength);
    }
    if (pattern != null) {
      schema.addProperty("pattern", pattern);
    }
    return schema;
  }

  private static JsonObject registryId() {
    return string(1, 256, REGISTRY_ID_PATTERN);
  }

  private static JsonObject enumString(String... values) {
    JsonObject schema = typed("string");
    JsonArray allowed = new JsonArray();
    for (String value : values) {
      allowed.add(value);
    }
    schema.add("enum", allowed);
    return schema;
  }

  private static JsonObject nullableEnumString(String... values) {
    JsonObject schema = enumString(values);
    JsonArray types = new JsonArray();
    types.add("string");
    types.add("null");
    schema.add("type", types);
    schema.getAsJsonArray("enum").add(com.google.gson.JsonNull.INSTANCE);
    return schema;
  }

  private static JsonObject number() {
    return typed("number");
  }

  private static JsonObject number(Double minimum, Double maximum) {
    JsonObject schema = number();
    if (minimum != null) {
      schema.addProperty("minimum", minimum);
    }
    if (maximum != null) {
      schema.addProperty("maximum", maximum);
    }
    return schema;
  }

  private static JsonObject integer() {
    return typed("integer");
  }

  private static JsonObject integer(Integer minimum, Integer maximum) {
    JsonObject schema = integer();
    if (minimum != null) {
      schema.addProperty("minimum", minimum);
    }
    if (maximum != null) {
      schema.addProperty("maximum", maximum);
    }
    return schema;
  }

  private static JsonObject bool() {
    return typed("boolean");
  }

  private static JsonObject nullable(JsonObject requiredSchema) {
    JsonObject schema = requiredSchema.deepCopy();
    String declaredType = schema.get("type").getAsString();
    JsonArray types = new JsonArray();
    types.add(declaredType);
    types.add("null");
    schema.add("type", types);
    return schema;
  }

  private static JsonObject typed(String type) {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", type);
    return schema;
  }

  private static Property property(String name, JsonObject schema) {
    return new Property(name, schema);
  }

  private record Property(String name, JsonObject schema) {}
}
