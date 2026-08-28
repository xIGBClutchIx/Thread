package me.clutchy.thread.transport.mcp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

/** Immutable local HTTP listener and server-identity settings for the Thread MCP adapter. */
public record McpServerOptions(
    InetAddress bindAddress,
    int port,
    int maxRequestBytes,
    int maxConcurrentRequests,
    String serverName,
    String serverVersion) {
  static final int DEFAULT_MAX_REQUEST_BYTES = 1_048_576;

  static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 8;

  public McpServerOptions {
    Objects.requireNonNull(bindAddress, "bindAddress");
    if (!bindAddress.isLoopbackAddress()) {
      throw new IllegalArgumentException("Thread MCP bind address must be loopback");
    }
    if (port < 0 || port > 65_535) {
      throw new IllegalArgumentException("port must be between 0 and 65535");
    }
    if (maxRequestBytes < 1) {
      throw new IllegalArgumentException("maxRequestBytes must be positive");
    }
    if (maxConcurrentRequests < 1) {
      throw new IllegalArgumentException("maxConcurrentRequests must be positive");
    }
    if (serverName == null || serverName.isBlank()) {
      throw new IllegalArgumentException("serverName must not be blank");
    }
    if (serverVersion == null || serverVersion.isBlank()) {
      throw new IllegalArgumentException("serverVersion must not be blank");
    }
  }

  /** Returns loopback options using an ephemeral port for isolated transport tests. */
  static McpServerOptions ephemeral(String serverVersion) {
    return new McpServerOptions(
        ipv4Loopback(),
        0,
        DEFAULT_MAX_REQUEST_BYTES,
        DEFAULT_MAX_CONCURRENT_REQUESTS,
        "Thread",
        serverVersion);
  }

  /** Returns validated persistent listener settings for the supplied Thread version. */
  public static McpServerOptions configured(
      String bindHost,
      int port,
      int maxRequestBytes,
      int maxConcurrentRequests,
      String serverVersion) {
    return new McpServerOptions(
        explicitLoopback(bindHost),
        port,
        maxRequestBytes,
        maxConcurrentRequests,
        "Thread",
        serverVersion);
  }

  private static InetAddress ipv4Loopback() {
    try {
      return InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
    } catch (UnknownHostException exception) {
      throw new AssertionError("IPv4 loopback literal should always be valid", exception);
    }
  }

  private static InetAddress explicitLoopback(String host) {
    Objects.requireNonNull(host, "bindHost");
    return switch (host.strip().toLowerCase(Locale.ROOT)) {
      case "127.0.0.1", "localhost" -> ipv4Loopback();
      case "::1", "[::1]" -> ipv6Loopback();
      default ->
          throw new IllegalArgumentException("Thread MCP bind host must be explicit loopback");
    };
  }

  private static InetAddress ipv6Loopback() {
    byte[] address = new byte[16];
    address[15] = 1;
    try {
      return InetAddress.getByAddress(address);
    } catch (UnknownHostException exception) {
      throw new AssertionError("IPv6 loopback literal should always be valid", exception);
    }
  }
}
