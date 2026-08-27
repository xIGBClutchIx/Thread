package me.clutchy.thread.core.tool;

import java.util.List;
import java.util.Objects;

/** Describes how one Minecraft-version adapter supports a transport-neutral Thread tool. */
public record ToolSupport(Level level, List<String> missingOptionalFields) {
  /** Supported states understood by the version-neutral runtime. */
  public enum Level {
    /** The adapter supplies the complete current tool contract. */
    FULLY_SUPPORTED,
    /** The adapter cannot safely implement the tool, so it must not be advertised. */
    UNSUPPORTED,
    /** The tool is usable, but explicitly optional result fields are unavailable. */
    SUPPORTED_WITH_MISSING_OPTIONAL_FIELDS
  }

  public ToolSupport {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(missingOptionalFields, "missingOptionalFields");
    missingOptionalFields =
        missingOptionalFields.stream()
            .map(field -> requireField(field))
            .distinct()
            .sorted()
            .toList();
    if (level == Level.SUPPORTED_WITH_MISSING_OPTIONAL_FIELDS && missingOptionalFields.isEmpty()) {
      throw new IllegalArgumentException("missing optional fields must be identified");
    }
    if (level != Level.SUPPORTED_WITH_MISSING_OPTIONAL_FIELDS && !missingOptionalFields.isEmpty()) {
      throw new IllegalArgumentException("only partial tool support may identify missing fields");
    }
  }

  /** Returns complete support for the current tool contract. */
  public static ToolSupport fullySupported() {
    return new ToolSupport(Level.FULLY_SUPPORTED, List.of());
  }

  /** Returns unsupported status, which keeps the tool out of discovery. */
  public static ToolSupport unsupported() {
    return new ToolSupport(Level.UNSUPPORTED, List.of());
  }

  /** Returns usable support with the named contract-defined optional fields unavailable. */
  public static ToolSupport missingOptionalFields(List<String> fields) {
    return new ToolSupport(Level.SUPPORTED_WITH_MISSING_OPTIONAL_FIELDS, fields);
  }

  /** Reports whether the tool remains safe to advertise and invoke. */
  public boolean advertised() {
    return level != Level.UNSUPPORTED;
  }

  private static String requireField(String field) {
    Objects.requireNonNull(field, "missingOptionalFields entry");
    String normalized = field.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("missing optional field must not be blank");
    }
    return normalized;
  }
}
