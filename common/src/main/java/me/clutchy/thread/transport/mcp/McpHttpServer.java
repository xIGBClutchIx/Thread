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
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import me.clutchy.thread.core.tool.ToolRegistry;

/**
 * Loopback-only MCP Streamable HTTP server backed by a {@link ToolRegistry}.
 *
 * <p>The endpoint accepts both the current stateless discovery flow and the initialization flow
 * used by Codex without introducing protocol sessions or a second transport endpoint.
 */
public final class McpHttpServer implements AutoCloseable {
  /** The single MCP Streamable HTTP endpoint path. */
  public static final String ENDPOINT_PATH = "/mcp";

  private static final String JSON_MEDIA_TYPE = "application/json";
  private static final String SSE_MEDIA_TYPE = "text/event-stream";
  private static final System.Logger LOGGER = System.getLogger(McpHttpServer.class.getName());

  private final HttpServer server;
  private final ExecutorService executor;
  private final McpServerOptions options;
  private final McpJsonRpcHandler rpc;
  private final Semaphore requestPermits;
  private final AtomicBoolean running = new AtomicBoolean();

  private McpHttpServer(ToolRegistry tools, McpServerOptions options) throws IOException {
    this.options = Objects.requireNonNull(options, "options");
    rpc = new McpJsonRpcHandler(Objects.requireNonNull(tools, "tools"), options);
    requestPermits = new Semaphore(options.maxConcurrentRequests());
    server = HttpServer.create(new InetSocketAddress(options.bindAddress(), options.port()), 0);
    executor = Executors.newVirtualThreadPerTaskExecutor();
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
    long started = System.nanoTime();
    if (!requestPermits.tryAcquire()) {
      McpHttpResponse busy =
          rpc.invalidRequest("Thread MCP is busy; retry the request later").withStatus(503);
      write(exchange, busy);
      logCompletion(busy.status(), started);
      return;
    }
    McpHttpResponse response;
    try {
      try {
        response = validateAndHandle(exchange);
      } catch (RuntimeException exception) {
        // Do not log request bodies, arguments, exception messages, or state-bearing results.
        LOGGER.log(
            System.Logger.Level.ERROR,
            "MCP request failed unexpectedly ({0})",
            exception.getClass().getName());
        response = rpc.internalError(JsonNull.INSTANCE);
      }
      write(exchange, response);
      logCompletion(response.status(), started);
    } finally {
      requestPermits.release();
    }
  }

  private static void logCompletion(int status, long started) {
    long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
    LOGGER.log(
        System.Logger.Level.DEBUG,
        "MCP request completed with HTTP {0} in {1} ms",
        status,
        elapsedMillis);
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
    return rpc.handle(
        parsed.getAsJsonObject(), exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
  }

  private static void write(HttpExchange exchange, McpHttpResponse response) throws IOException {
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
