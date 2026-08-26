package me.clutchy.thread.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Loads and creates Thread's versioned, persistent JSON configuration. */
public final class ThreadConfigLoader {
  /** Current on-disk configuration schema. */
  public static final int SCHEMA_VERSION = 1;

  private static final int MAX_CONFIG_BYTES = 1024 * 1024;
  private static final Gson PRETTY_JSON =
      new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
  private static final Set<String> FIELDS =
      Set.of(
          "schemaVersion",
          "mcpEnabled",
          "mcpBindHost",
          "mcpPort",
          "enabledTools",
          "disabledIntegrations",
          "maxEntityRadius",
          "maxEntityResults",
          "maxItemSearchResults",
          "maxRequestBytes",
          "gameThreadTimeoutMillis",
          "maxConcurrentRequests");

  private ThreadConfigLoader() {}

  /**
   * Loads a configuration, creating a documented default file when it does not exist.
   *
   * <p>An invalid existing file is never replaced. Callers may fall back to defaults while leaving
   * the player's input intact for correction.
   *
   * @param path configuration file path
   * @return validated configuration
   * @throws IOException when the file cannot be read or created
   * @throws IllegalArgumentException when existing content is invalid
   */
  public static ThreadConfig loadOrCreate(Path path) throws IOException {
    Path normalized = path.toAbsolutePath().normalize();
    if (Files.notExists(normalized)) {
      Path parent = normalized.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      try {
        Files.writeString(
            normalized,
            serialize(ThreadConfig.defaults()),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE);
      } catch (FileAlreadyExistsException ignored) {
        // Another loader won the creation race; read the completed file below.
      }
    }
    long size = Files.size(normalized);
    if (size > MAX_CONFIG_BYTES) {
      throw new IllegalArgumentException("Thread configuration exceeds 1048576 bytes");
    }
    return parse(Files.readString(normalized, StandardCharsets.UTF_8));
  }

  static ThreadConfig parse(String source) {
    final JsonElement parsed;
    try {
      parsed = JsonParser.parseString(source);
    } catch (JsonParseException exception) {
      throw new IllegalArgumentException("Thread configuration is not valid JSON", exception);
    }
    if (!parsed.isJsonObject()) {
      throw new IllegalArgumentException("Thread configuration root must be an object");
    }

    JsonObject object = parsed.getAsJsonObject();
    for (String field : object.keySet()) {
      if (!FIELDS.contains(field)) {
        throw new IllegalArgumentException("Unknown Thread configuration field: " + field);
      }
    }
    int schemaVersion = requiredInt(object, "schemaVersion");
    if (schemaVersion != SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Unsupported Thread configuration schema: " + schemaVersion);
    }

    ThreadConfig defaults = ThreadConfig.defaults();
    return new ThreadConfig(
        optionalBoolean(object, "mcpEnabled", defaults.mcpEnabled()),
        optionalString(object, "mcpBindHost", defaults.mcpBindHost()),
        optionalInt(object, "mcpPort", defaults.mcpPort()),
        optionalStrings(object, "enabledTools", defaults.enabledTools()),
        optionalStrings(object, "disabledIntegrations", defaults.disabledIntegrations()),
        optionalDouble(object, "maxEntityRadius", defaults.maxEntityRadius()),
        optionalInt(object, "maxEntityResults", defaults.maxEntityResults()),
        optionalInt(object, "maxItemSearchResults", defaults.maxItemSearchResults()),
        optionalInt(object, "maxRequestBytes", defaults.maxRequestBytes()),
        optionalLong(object, "gameThreadTimeoutMillis", defaults.gameThreadTimeoutMillis()),
        optionalInt(object, "maxConcurrentRequests", defaults.maxConcurrentRequests()));
  }

  static String serialize(ThreadConfig config) {
    JsonObject object = new JsonObject();
    object.addProperty("schemaVersion", SCHEMA_VERSION);
    object.addProperty("mcpEnabled", config.mcpEnabled());
    object.addProperty("mcpBindHost", config.mcpBindHost());
    object.addProperty("mcpPort", config.mcpPort());
    JsonArray enabledTools = new JsonArray();
    config.enabledTools().forEach(enabledTools::add);
    object.add("enabledTools", enabledTools);
    JsonArray disabledIntegrations = new JsonArray();
    config.disabledIntegrations().forEach(disabledIntegrations::add);
    object.add("disabledIntegrations", disabledIntegrations);
    object.addProperty("maxEntityRadius", config.maxEntityRadius());
    object.addProperty("maxEntityResults", config.maxEntityResults());
    object.addProperty("maxItemSearchResults", config.maxItemSearchResults());
    object.addProperty("maxRequestBytes", config.maxRequestBytes());
    object.addProperty("gameThreadTimeoutMillis", config.gameThreadTimeoutMillis());
    object.addProperty("maxConcurrentRequests", config.maxConcurrentRequests());
    return PRETTY_JSON.toJson(object) + System.lineSeparator();
  }

  private static boolean optionalBoolean(JsonObject object, String name, boolean fallback) {
    if (!object.has(name)) {
      return fallback;
    }
    JsonPrimitive value = primitive(object, name);
    if (!value.isBoolean()) {
      throw typeError(name, "a boolean");
    }
    return value.getAsBoolean();
  }

  private static String optionalString(JsonObject object, String name, String fallback) {
    if (!object.has(name)) {
      return fallback;
    }
    JsonPrimitive value = primitive(object, name);
    if (!value.isString()) {
      throw typeError(name, "a string");
    }
    return value.getAsString();
  }

  private static List<String> optionalStrings(
      JsonObject object, String name, List<String> fallback) {
    if (!object.has(name)) {
      return fallback;
    }
    JsonElement value = object.get(name);
    if (!value.isJsonArray()) {
      throw typeError(name, "an array of strings");
    }
    List<String> strings = new ArrayList<>();
    for (JsonElement item : value.getAsJsonArray()) {
      if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
        throw typeError(name, "an array of strings");
      }
      strings.add(item.getAsString());
    }
    return strings;
  }

  private static int requiredInt(JsonObject object, String name) {
    if (!object.has(name)) {
      throw new IllegalArgumentException("Missing Thread configuration field: " + name);
    }
    return intValue(object, name);
  }

  private static int optionalInt(JsonObject object, String name, int fallback) {
    return object.has(name) ? intValue(object, name) : fallback;
  }

  private static long optionalLong(JsonObject object, String name, long fallback) {
    if (!object.has(name)) {
      return fallback;
    }
    try {
      return number(object, name).getAsBigDecimal().longValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw typeError(name, "an integer", exception);
    }
  }

  private static double optionalDouble(JsonObject object, String name, double fallback) {
    if (!object.has(name)) {
      return fallback;
    }
    return number(object, name).getAsDouble();
  }

  private static int intValue(JsonObject object, String name) {
    try {
      return number(object, name).getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw typeError(name, "an integer", exception);
    }
  }

  private static JsonPrimitive number(JsonObject object, String name) {
    JsonPrimitive value = primitive(object, name);
    if (!value.isNumber()) {
      throw typeError(name, "a number");
    }
    return value;
  }

  private static JsonPrimitive primitive(JsonObject object, String name) {
    JsonElement value = object.get(name);
    if (value == null || !value.isJsonPrimitive()) {
      throw typeError(name, "a scalar value");
    }
    return value.getAsJsonPrimitive();
  }

  private static IllegalArgumentException typeError(String name, String expected) {
    return new IllegalArgumentException(
        "Thread configuration field " + name + " must be " + expected);
  }

  private static IllegalArgumentException typeError(
      String name, String expected, RuntimeException cause) {
    return new IllegalArgumentException(
        "Thread configuration field " + name + " must be " + expected, cause);
  }
}
