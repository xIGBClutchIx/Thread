package dev.xigbclutch.thread.core.tool;

import java.util.Objects;

/** Capability metadata exposed during tool discovery. */
public record ToolCapabilities(boolean readOnly, ToolAvailability availability) {
  public ToolCapabilities {
    Objects.requireNonNull(availability, "availability");
  }

  /** Metadata for a read-only tool callable in any client state. */
  public static ToolCapabilities alwaysAvailable() {
    return new ToolCapabilities(true, ToolAvailability.ALWAYS);
  }

  /** Metadata for a read-only gameplay tool requiring supported single-player. */
  public static ToolCapabilities supportedSingleplayer() {
    return new ToolCapabilities(true, ToolAvailability.SUPPORTED_SINGLEPLAYER);
  }
}
