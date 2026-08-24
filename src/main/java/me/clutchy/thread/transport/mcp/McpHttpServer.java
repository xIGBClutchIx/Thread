package me.clutchy.thread.transport.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import me.clutchy.thread.core.tool.ToolRegistry;

/** Loopback-only MCP 2026-07-28 Streamable HTTP server backed by a {@link ToolRegistry}. */
public final class McpHttpServer implements AutoCloseable {
  /** The single MCP Streamable HTTP endpoint path. */
  public static final String ENDPOINT_PATH = "/mcp";

  private static final String JSON_MEDIA_TYPE = "application/json";
  private static final String SESSION_ID_HEADER = "Mcp-Session-Id";
  private static final String SSE_MEDIA_TYPE = "text/event-stream";

  private final HttpServer server;
  private final ExecutorService executor;
  private final McpServerOptions options;
  private final McpJsonRpcHandler rpc;
  private final AtomicBoolean running = new AtomicBoolean();

  private McpHttpServer(ToolRegistry tools, McpServerOptions options) throws IOException {
    this.options = Objects.requireNonNull(options, "options");
    rpc = new McpJsonRpcHandler(Objects.requireNonNull(tools, "tools"), options);
    server = HttpServer.create(new InetSocketAddress(options.bindAddress(), options.port()), 0);
    executor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofPlatform().daemon(true).name("thread-mcp-", 0).factory());
    server.setExecutor(executor);
    server.createContext(ENDPOINT_PATH, this::handle);
  }

  /** Creates, binds, and starts a local MCP listener. */
  public static McpHttpServer start(ToolRegistry tools, McpServerOptions options)
      throws IOException {
    McpHttpServer transport = new McpHttpServer(tools, options);
    transport.server.start();
    transport.running.set(true);
    return transport;
  }

  /** Returns the concrete endpoint, including an assigned ephemeral port when requested. */
  public URI endpoint() {
    String address = server.getAddress().getAddress().getHostAddress();
    if (address.contains(":")) {
      address = "[" + address + "]";
    }
    return URI.create("http://" + address + ":" + server.getAddress().getPort() + ENDPOINT_PATH);
  }

  /** Returns whether the listener has started and has not been closed. */
  public boolean running() {
    return running.get();
  }

  /** Stops accepting requests and releases listener and worker resources. */
  @Override
  public void close() {
    if (running.compareAndSet(true, false)) {
      server.stop(0);
      executor.shutdownNow();
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    McpHttpResponse response;
    try {
      response = validateAndHandle(exchange);
    } catch (RuntimeException exception) {
      response = rpc.internalError(JsonNull.INSTANCE);
    }
    write(exchange, response);
  }

  private McpHttpResponse validateAndHandle(HttpExchange exchange) throws IOException {
    if (!exchange.getRequestURI().getRawPath().equals(ENDPOINT_PATH)) {
      return McpHttpResponse.empty(404);
    }
    if (!exchange.getRequestMethod().equals("POST")) {
      exchange.getResponseHeaders().set("Allow", "POST");
      return McpHttpResponse.empty(405);
    }
    if (!validOrigin(exchange.getRequestHeaders().getFirst("Origin"))) {
      return rpc.invalidRequest("Forbidden Origin").withStatus(403);
    }
    if (exchange.getRequestHeaders().containsKey(SESSION_ID_HEADER)) {
      return rpc.invalidRequest("Mcp-Session-Id is not supported by MCP 2026-07-28");
    }
    if (!mediaType(exchange.getRequestHeaders().getFirst("Content-Type")).equals(JSON_MEDIA_TYPE)) {
      return rpc.invalidRequest("Content-Type must be application/json").withStatus(415);
    }
    if (!acceptsRequiredMediaTypes(exchange.getRequestHeaders().get("Accept"))) {
      return rpc.invalidRequest("Accept must include application/json and text/event-stream")
          .withStatus(406);
    }

    long declaredLength =
        parseContentLength(exchange.getRequestHeaders().getFirst("Content-Length"));
    if (declaredLength > options.maxRequestBytes()) {
      return rpc.invalidRequest("Request body exceeds the configured limit").withStatus(413);
    }
    byte[] body;
    try (InputStream input = exchange.getRequestBody()) {
      body = input.readNBytes(options.maxRequestBytes() + 1);
    }
    if (body.length > options.maxRequestBytes()) {
      return rpc.invalidRequest("Request body exceeds the configured limit").withStatus(413);
    }

    JsonElement parsed;
    try {
      parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
    } catch (JsonParseException exception) {
      return rpc.parseError();
    }
    if (!parsed.isJsonObject()) {
      return rpc.invalidRequest("Request body must be one JSON-RPC object");
    }
    McpRequestHeaders headers =
        new McpRequestHeaders(
            exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"),
            exchange.getRequestHeaders().getFirst("Mcp-Method"),
            exchange.getRequestHeaders().getFirst("Mcp-Name"));
    return rpc.handle(parsed.getAsJsonObject(), headers);
  }

  private static void write(HttpExchange exchange, McpHttpResponse response) throws IOException {
    exchange.getResponseHeaders().set("MCP-Protocol-Version", McpJsonRpcHandler.PROTOCOL_VERSION);
    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    if (!response.hasBody()) {
      exchange.sendResponseHeaders(response.status(), -1);
      exchange.close();
      return;
    }
    byte[] body = response.body().toString().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", JSON_MEDIA_TYPE + "; charset=utf-8");
    exchange.sendResponseHeaders(response.status(), body.length);
    try (exchange;
        var output = exchange.getResponseBody()) {
      output.write(body);
    }
  }

  private static boolean validOrigin(String origin) {
    if (origin == null) {
      return true;
    }
    try {
      URI uri = new URI(origin);
      String host = uri.getHost();
      return (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
          && host != null
          && uri.getRawUserInfo() == null
          && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null
          && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"));
    } catch (NullPointerException | URISyntaxException exception) {
      return false;
    }
  }

  private static boolean acceptsRequiredMediaTypes(List<String> headers) {
    if (headers == null || headers.isEmpty()) {
      return false;
    }
    boolean acceptsJson = false;
    boolean acceptsEvents = false;
    for (String header : headers) {
      for (String value : header.split(",")) {
        String acceptedType = mediaType(value);
        acceptsJson |= acceptedType.equals(JSON_MEDIA_TYPE);
        acceptsEvents |= acceptedType.equals(SSE_MEDIA_TYPE);
      }
    }
    return acceptsJson && acceptsEvents;
  }

  private static String mediaType(String header) {
    if (header == null) {
      return "";
    }
    int parameters = header.indexOf(';');
    return (parameters < 0 ? header : header.substring(0, parameters))
        .trim()
        .toLowerCase(Locale.ROOT);
  }

  private static long parseContentLength(String header) {
    if (header == null) {
      return -1;
    }
    try {
      return Long.parseLong(header);
    } catch (NumberFormatException exception) {
      return Long.MAX_VALUE;
    }
  }
}
