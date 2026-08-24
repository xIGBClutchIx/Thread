package dev.xigbclutch.thread.config;

/**
 * Minimal user-facing configuration established by the repository foundation.
 *
 * <p>Slice 0 defines defaults only. Persistent loading, validation, and runtime application belong
 * to the later configuration slice.
 *
 * @param mcpEnabled whether the future MCP transport should start when it is implemented
 */
public record ThreadConfig(boolean mcpEnabled) {
  /**
   * Returns the V1 foundation defaults.
   *
   * @return a configuration with the local MCP transport enabled
   */
  public static ThreadConfig defaults() {
    return new ThreadConfig(true);
  }
}
