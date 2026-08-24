package me.clutchy.thread.config;

/**
 * Minimal user-facing configuration established by the repository foundation.
 *
 * <p>Persistent loading and expanded transport settings belong to the later configuration slice.
 *
 * @param mcpEnabled whether the local MCP transport should start with the client
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
