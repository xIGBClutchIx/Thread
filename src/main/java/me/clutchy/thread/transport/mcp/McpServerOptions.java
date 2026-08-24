package me.clutchy.thread.transport.mcp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/** Immutable local HTTP listener and server-identity settings for the Thread MCP adapter. */
public record McpServerOptions(
    InetAddress bindAddress,
    int port,
    int maxRequestBytes,
    String serverName,
    String serverVersion) {
  /** Default local port used until persistent transport configuration is introduced. */
  public static final int DEFAULT_PORT = 25_580;

  /** Fixed safety bound used until Slice 5 makes the request limit configurable. */
  public static final int DEFAULT_MAX_REQUEST_BYTES = 1_048_576;

  public McpServerOptions {
    Objects.requireNonNull(bindAddress, "bindAddress");
    if (!bindAddress.isLoopbackAddress()) {
      throw new IllegalArgumentException("V1 MCP bind address must be loopback");
    }
    if (port < 0 || port > 65_535) {
      throw new IllegalArgumentException("port must be between 0 and 65535");
    }
    if (maxRequestBytes < 1) {
      throw new IllegalArgumentException("maxRequestBytes must be positive");
    }
    if (serverName == null || serverName.isBlank()) {
      throw new IllegalArgumentException("serverName must not be blank");
    }
    if (serverVersion == null || serverVersion.isBlank()) {
      throw new IllegalArgumentException("serverVersion must not be blank");
    }
  }

  /** Returns the V1 loopback listener defaults for the supplied Thread version. */
  public static McpServerOptions loopbackDefaults(String serverVersion) {
    return new McpServerOptions(
        ipv4Loopback(), DEFAULT_PORT, DEFAULT_MAX_REQUEST_BYTES, "Thread", serverVersion);
  }

  /** Returns loopback options using an ephemeral port, primarily for isolated tests. */
  public static McpServerOptions ephemeral(String serverVersion) {
    return new McpServerOptions(
        ipv4Loopback(), 0, DEFAULT_MAX_REQUEST_BYTES, "Thread", serverVersion);
  }

  private static InetAddress ipv4Loopback() {
    try {
      return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    } catch (UnknownHostException exception) {
      throw new AssertionError("IPv4 loopback literal should always be valid", exception);
    }
  }
}
