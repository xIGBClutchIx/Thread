package me.clutchy.thread.core.model.validation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Shared constructor validation for Thread's immutable model records. */
public final class ModelValidation {
  private static final Pattern REGISTRY_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

  private ModelValidation() {}

  public static String nonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  public static String boundedNonBlank(String value, String name, int maximumCodePoints) {
    nonBlank(value, name);
    if (value.codePointCount(0, value.length()) > maximumCodePoints) {
      throw new IllegalArgumentException(
          name + " must not exceed " + maximumCodePoints + " characters");
    }
    return value;
  }

  public static String optionalBoundedNonBlank(String value, String name, int maximumCodePoints) {
    return value == null ? null : boundedNonBlank(value, name, maximumCodePoints);
  }

  public static String optionalRegistryId(String value, String name) {
    return value == null ? null : registryId(value, name);
  }

  public static String registryId(String value, String name) {
    nonBlank(value, name);
    if (!REGISTRY_ID.matcher(value).matches()) {
      throw new IllegalArgumentException(name + " must be a canonical registry ID: " + value);
    }
    return value;
  }

  public static double finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
    return value;
  }

  public static double nonNegative(double value, String name) {
    finite(value, name);
    if (value < 0) {
      throw new IllegalArgumentException(name + " must not be negative");
    }
    return value;
  }

  public static <T> List<T> immutableList(List<T> values, String name) {
    Objects.requireNonNull(values, name);
    return List.copyOf(values);
  }

  public static Map<String, String> immutableSortedMap(Map<String, String> values, String name) {
    Objects.requireNonNull(values, name);
    TreeMap<String, String> copy = new TreeMap<>();
    values.forEach(
        (key, value) -> copy.put(nonBlank(key, name + " key"), Objects.requireNonNull(value)));
    return Collections.unmodifiableMap(copy);
  }
}
