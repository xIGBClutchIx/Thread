package me.clutchy.thread.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import me.clutchy.thread.core.tool.ToolId;

/**
 * Persistent V1 settings for the local transport and bounded game-state queries.
 *
 * <p>Values are rejected instead of silently clamped so unsafe or mistyped configuration cannot
 * weaken Thread's limits without the player noticing.
 *
 * @param mcpEnabled whether the local MCP transport should start with the client
 * @param mcpBindHost loopback host used by the local MCP transport
 * @param mcpPort TCP port used by the local MCP transport
 * @param enabledTools exact tool IDs or namespace wildcards such as {@code minecraft.*}
 * @param maxEntityRadius maximum accepted nearby-entity radius
 * @param maxEntityResults maximum returned nearby entities
 * @param maxItemSearchResults maximum returned item-search matches
 * @param maxRequestBytes maximum accepted HTTP request body size
 * @param gameThreadTimeoutMillis maximum wait for a game-thread state read
 * @param maxConcurrentRequests maximum MCP requests handled concurrently
 */
public record ThreadConfig(
    boolean mcpEnabled,
    String mcpBindHost,
    int mcpPort,
    List<String> enabledTools,
    double maxEntityRadius,
    int maxEntityResults,
    int maxItemSearchResults,
    int maxRequestBytes,
    long gameThreadTimeoutMillis,
    int maxConcurrentRequests) {
  /** Largest entity radius accepted from persistent configuration. */
  public static final double HARD_MAX_ENTITY_RADIUS = 128.0D;

  /** Largest entity result count accepted from persistent configuration. */
  public static final int HARD_MAX_ENTITY_RESULTS = 512;

  /** Largest item result count accepted from persistent configuration. */
  public static final int HARD_MAX_ITEM_SEARCH_RESULTS = 256;

  /** Largest HTTP body limit accepted from persistent configuration. */
  public static final int HARD_MAX_REQUEST_BYTES = 8 * 1024 * 1024;

  /** Longest game-thread wait accepted from persistent configuration. */
  public static final long HARD_MAX_GAME_THREAD_TIMEOUT_MILLIS = 30_000L;

  /** Largest MCP request concurrency accepted from persistent configuration. */
  public static final int HARD_MAX_CONCURRENT_REQUESTS = 32;

  public ThreadConfig {
    mcpBindHost = normalizeLoopbackHost(mcpBindHost);
    requireRange(mcpPort, 1, 65_535, "mcpPort");
    enabledTools = normalizeSelectors(enabledTools);
    requireRange(maxEntityRadius, 0.0D, HARD_MAX_ENTITY_RADIUS, "maxEntityRadius");
    requireRange(maxEntityResults, 1, HARD_MAX_ENTITY_RESULTS, "maxEntityResults");
    requireRange(maxItemSearchResults, 1, HARD_MAX_ITEM_SEARCH_RESULTS, "maxItemSearchResults");
    requireRange(maxRequestBytes, 1, HARD_MAX_REQUEST_BYTES, "maxRequestBytes");
    requireRange(
        gameThreadTimeoutMillis,
        1L,
        HARD_MAX_GAME_THREAD_TIMEOUT_MILLIS,
        "gameThreadTimeoutMillis");
    requireRange(maxConcurrentRequests, 1, HARD_MAX_CONCURRENT_REQUESTS, "maxConcurrentRequests");
  }

  /**
   * Returns conservative V1 defaults.
   *
   * @return loopback-only settings with every V1 Minecraft tool enabled
   */
  public static ThreadConfig defaults() {
    return new ThreadConfig(
        true, "127.0.0.1", 25_580, List.of("minecraft.*"), 64.0D, 128, 64, 1024 * 1024, 5_000L, 8);
  }

  /** Returns whether an exact tool ID is enabled by the configured selectors. */
  public boolean toolEnabled(ToolId toolId) {
    String value = Objects.requireNonNull(toolId, "toolId").value();
    return enabledTools.stream()
        .anyMatch(
            selector ->
                selector.equals(value)
                    || (selector.endsWith(".*")
                        && value.startsWith(selector.substring(0, selector.length() - 1))));
  }

  private static List<String> normalizeSelectors(List<String> selectors) {
    Objects.requireNonNull(selectors, "enabledTools");
    return selectors.stream()
        .map(selector -> requireText(selector, "enabledTools entry").toLowerCase(Locale.ROOT))
        .peek(ThreadConfig::validateSelector)
        .distinct()
        .sorted()
        .toList();
  }

  private static void validateSelector(String selector) {
    if (selector.endsWith(".*")) {
      ToolId.of(selector.substring(0, selector.length() - 2) + ".placeholder");
      return;
    }
    ToolId.of(selector);
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return normalized;
  }

  private static String normalizeLoopbackHost(String value) {
    String normalized = requireText(value, "mcpBindHost").toLowerCase(Locale.ROOT);
    if ("[::1]".equals(normalized)) {
      normalized = "::1";
    }
    if (!"127.0.0.1".equals(normalized)
        && !"localhost".equals(normalized)
        && !"::1".equals(normalized)) {
      throw new IllegalArgumentException("mcpBindHost must be an explicit loopback host");
    }
    return normalized;
  }

  private static void requireRange(int value, int minimum, int maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
    }
  }

  private static void requireRange(long value, long minimum, long maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
    }
  }

  private static void requireRange(double value, double minimum, double maximum, String name) {
    if (!Double.isFinite(value) || value <= minimum || value > maximum) {
      throw new IllegalArgumentException(
          name + " must be greater than " + minimum + " and at most " + maximum);
    }
  }
}
