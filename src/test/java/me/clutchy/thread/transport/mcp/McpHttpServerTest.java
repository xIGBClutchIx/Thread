package me.clutchy.thread.transport.mcp;

import static me.clutchy.thread.core.testing.TestJsonContracts.echoTool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.serialization.JsonCodec;
import me.clutchy.thread.core.testing.TestJsonContracts;
import me.clutchy.thread.core.tool.GameTool;
import me.clutchy.thread.core.tool.ToolCapabilities;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class McpHttpServerTest {
  private static final String VERSION = McpJsonRpcHandler.PROTOCOL_VERSION;

  @Test
  void discoversRegistryCatalogAndInvokesStructuredResults() throws Exception {
    ToolRegistry registry = registryWithEchoTool();
    try (McpHttpServer server =
        McpHttpServer.start(registry, McpServerOptions.ephemeral("0.1.0"))) {
      assertEquals("127.0.0.1", server.endpoint().getHost());
      assertTrue(server.running());

      HttpResponse<String> discover = post(server.endpoint(), request(1, "server/discover"));
      assertEquals(200, discover.statusCode());
      JsonObject discoverResult = body(discover).getAsJsonObject("result");
      assertEquals("complete", discoverResult.get("resultType").getAsString());
      assertEquals(
          VERSION, discoverResult.getAsJsonArray("supportedVersions").get(0).getAsString());
      assertTrue(discoverResult.getAsJsonObject("capabilities").has("tools"));
      JsonObject serverInfo =
          discoverResult
              .getAsJsonObject("_meta")
              .getAsJsonObject(McpJsonRpcHandler.SERVER_INFO_META);
      assertEquals("Thread", serverInfo.get("name").getAsString());
      assertEquals("0.1.0", serverInfo.get("version").getAsString());
      assertEquals(60_000, discoverResult.get("ttlMs").getAsLong());
      assertEquals("public", discoverResult.get("cacheScope").getAsString());
      assertTrue(discover.headers().firstValue("Mcp-Session-Id").isEmpty());

      HttpResponse<String> listed = post(server.endpoint(), request(2, "tools/list"));
      JsonObject listResult = body(listed).getAsJsonObject("result");
      assertServerInfo(listResult, "0.1.0");
      assertEquals(60_000, listResult.get("ttlMs").getAsLong());
      assertEquals("public", listResult.get("cacheScope").getAsString());
      JsonArray tools = listResult.getAsJsonArray("tools");
      assertEquals(1, tools.size());
      JsonObject echo = tools.get(0).getAsJsonObject();
      assertEquals("test.echo", echo.get("name").getAsString());
      assertEquals("object", echo.getAsJsonObject("inputSchema").get("type").getAsString());
      assertEquals("object", echo.getAsJsonObject("outputSchema").get("type").getAsString());
      assertTrue(echo.getAsJsonObject("annotations").get("readOnlyHint").getAsBoolean());

      JsonObject call = request(3, "tools/call");
      call.getAsJsonObject("params").addProperty("name", "test.echo");
      call.getAsJsonObject("params").add("arguments", object("message", "hello"));
      HttpResponse<String> called = post(server.endpoint(), call);
      JsonObject callResult = body(called).getAsJsonObject("result");
      assertEquals(200, called.statusCode());
      assertServerInfo(callResult, "0.1.0");
      assertFalse(callResult.get("isError").getAsBoolean());
      assertEquals(
          "hello", callResult.getAsJsonObject("structuredContent").get("message").getAsString());
      assertEquals(
          "{\"message\":\"hello\"}",
          callResult.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
    }
  }

  @Test
  void mapsToolFailuresInsideSuccessfulProtocolResults() throws Exception {
    try (McpHttpServer server =
        McpHttpServer.start(registryWithEchoTool(), McpServerOptions.ephemeral("test"))) {
      JsonObject call = request(7, "tools/call");
      call.getAsJsonObject("params").addProperty("name", "test.echo");
      call.getAsJsonObject("params").add("arguments", new JsonObject());

      HttpResponse<String> response = post(server.endpoint(), call);

      assertEquals(200, response.statusCode());
      JsonObject result = body(response).getAsJsonObject("result");
      assertTrue(result.get("isError").getAsBoolean());
      JsonObject error = result.getAsJsonObject("structuredContent");
      assertEquals("INVALID_INPUT", error.get("code").getAsString());
      assertEquals("$.message", error.getAsJsonObject("details").get("path").getAsString());
    }
  }

  @Test
  void rejectsUnknownToolsAndMethodsAsProtocolErrorsWithoutStopping() throws Exception {
    try (McpHttpServer server =
        McpHttpServer.start(registryWithEchoTool(), McpServerOptions.ephemeral("test"))) {
      JsonObject missingTool = request(8, "tools/call");
      missingTool.getAsJsonObject("params").addProperty("name", "test.missing");
      missingTool.getAsJsonObject("params").add("arguments", new JsonObject());
      HttpResponse<String> missing = post(server.endpoint(), missingTool);
      assertEquals(400, missing.statusCode());
      assertEquals(-32_602, errorCode(missing));

      HttpResponse<String> unknownMethod = post(server.endpoint(), request(9, "resources/list"));
      assertEquals(404, unknownMethod.statusCode());
      assertEquals(-32_601, errorCode(unknownMethod));

      assertEquals(200, post(server.endpoint(), request(10, "tools/list")).statusCode());
      assertTrue(server.running());
    }
  }

  @Test
  void validatesMirroredHeadersAndProtocolVersion() throws Exception {
    try (McpHttpServer server =
        McpHttpServer.start(registryWithEchoTool(), McpServerOptions.ephemeral("test"))) {
      JsonObject list = request(11, "tools/list");
      HttpResponse<String> missingMethod =
          send(server.endpoint(), list.toString(), VERSION, null, null, null, null);
      assertEquals(400, missingMethod.statusCode());
      assertEquals(McpJsonRpcHandler.HEADER_MISMATCH, errorCode(missingMethod));

      HttpResponse<String> mismatchedVersion =
          send(server.endpoint(), list.toString(), "2025-11-25", "tools/list", null, null, null);
      assertEquals(McpJsonRpcHandler.HEADER_MISMATCH, errorCode(mismatchedVersion));

      JsonObject unsupported = request(12, "tools/list", "2099-01-01");
      HttpResponse<String> unsupportedResponse =
          send(
              server.endpoint(),
              unsupported.toString(),
              "2099-01-01",
              "tools/list",
              null,
              null,
              null);
      assertEquals(400, unsupportedResponse.statusCode());
      assertEquals(McpJsonRpcHandler.UNSUPPORTED_PROTOCOL_VERSION, errorCode(unsupportedResponse));
      assertEquals(
          VERSION,
          body(unsupportedResponse)
              .getAsJsonObject("error")
              .getAsJsonObject("data")
              .getAsJsonArray("supported")
              .get(0)
              .getAsString());

      JsonObject call = request(13, "tools/call");
      call.getAsJsonObject("params").addProperty("name", "test.echo");
      call.getAsJsonObject("params").add("arguments", object("message", "encoded"));
      HttpResponse<String> mismatchedName =
          send(server.endpoint(), call.toString(), VERSION, "tools/call", "test.other", null, null);
      assertEquals(McpJsonRpcHandler.HEADER_MISMATCH, errorCode(mismatchedName));

      String encodedName =
          "=?base64?"
              + Base64.getEncoder().encodeToString("test.echo".getBytes(StandardCharsets.UTF_8))
              + "?=";
      HttpResponse<String> encoded =
          send(server.endpoint(), call.toString(), VERSION, "tools/call", encodedName, null, null);
      assertEquals(200, encoded.statusCode());
    }
  }

  @Test
  void requiresModernMetadataOnEveryRequest() throws Exception {
    try (McpHttpServer server =
        McpHttpServer.start(registryWithEchoTool(), McpServerOptions.ephemeral("test"))) {
      JsonObject missingVersion = request(14, "tools/list");
      missingVersion
          .getAsJsonObject("params")
          .getAsJsonObject("_meta")
          .remove(McpJsonRpcHandler.PROTOCOL_VERSION_META);
      HttpResponse<String> versionResponse =
          send(
              server.endpoint(),
              missingVersion.toString(),
              VERSION,
              "tools/list",
              null,
              null,
              null);
      assertEquals(400, versionResponse.statusCode());
      assertEquals(McpJsonRpcHandler.HEADER_MISMATCH, errorCode(versionResponse));

      JsonObject missingCapabilities = request(15, "tools/list");
      missingCapabilities
          .getAsJsonObject("params")
          .getAsJsonObject("_meta")
          .remove(McpJsonRpcHandler.CLIENT_CAPABILITIES_META);
      HttpResponse<String> capabilitiesResponse = post(server.endpoint(), missingCapabilities);
      assertEquals(400, capabilitiesResponse.statusCode());
      assertEquals(-32_602, errorCode(capabilitiesResponse));

      JsonObject call = request(16, "tools/call");
      call.getAsJsonObject("params").addProperty("name", "test.echo");
      call.getAsJsonObject("params").add("arguments", object("message", "missing header"));
      HttpResponse<String> nameResponse =
          send(server.endpoint(), call.toString(), VERSION, "tools/call", null, null, null);
      assertEquals(400, nameResponse.statusCode());
      assertEquals(McpJsonRpcHandler.HEADER_MISMATCH, errorCode(nameResponse));
    }
  }

  @Test
  void rejectsUnsafeHttpRequestsAndOversizedBodies() throws Exception {
    McpServerOptions defaults = McpServerOptions.ephemeral("test");
    McpServerOptions limited =
        new McpServerOptions(
            defaults.bindAddress(),
            0,
            256,
            defaults.maxConcurrentRequests(),
            defaults.serverName(),
            defaults.serverVersion());
    try (McpHttpServer server = McpHttpServer.start(registryWithEchoTool(), limited)) {
      JsonObject list = request(17, "tools/list");
      HttpResponse<String> hostileOrigin =
          send(
              server.endpoint(),
              list.toString(),
              VERSION,
              "tools/list",
              null,
              "https://attacker.example",
              null);
      assertEquals(403, hostileOrigin.statusCode());

      HttpResponse<String> wrongContentType =
          send(server.endpoint(), list.toString(), VERSION, "tools/list", null, null, "text/plain");
      assertEquals(415, wrongContentType.statusCode());

      HttpRequest incompleteAccept =
          HttpRequest.newBuilder(server.endpoint())
              .timeout(Duration.ofSeconds(5))
              .header("Accept", "application/json")
              .header("Content-Type", "application/json")
              .header("MCP-Protocol-Version", VERSION)
              .header("Mcp-Method", "tools/list")
              .POST(HttpRequest.BodyPublishers.ofString(list.toString()))
              .build();
      assertEquals(
          406,
          HttpClient.newHttpClient()
              .send(incompleteAccept, HttpResponse.BodyHandlers.ofString())
              .statusCode());

      String oversized = "{\"padding\":\"" + "x".repeat(300) + "\"}";
      HttpResponse<String> tooLarge =
          send(server.endpoint(), oversized, VERSION, "tools/list", null, null, null);
      assertEquals(413, tooLarge.statusCode());

      HttpResponse<String> malformed =
          send(server.endpoint(), "{", VERSION, "tools/list", null, null, null);
      assertEquals(400, malformed.statusCode());
      assertEquals(-32_700, errorCode(malformed));

      HttpRequest get =
          HttpRequest.newBuilder(server.endpoint()).GET().timeout(Duration.ofSeconds(5)).build();
      assertEquals(
          405,
          HttpClient.newHttpClient()
              .send(get, HttpResponse.BodyHandlers.discarding())
              .statusCode());
    }
  }

  @Test
  void acceptsLocalOrigins() throws Exception {
    try (McpHttpServer server =
        McpHttpServer.start(registryWithEchoTool(), McpServerOptions.ephemeral("test"))) {
      JsonObject list = request(18, "tools/list");
      HttpResponse<String> response =
          send(
              server.endpoint(),
              list.toString(),
              VERSION,
              "tools/list",
              null,
              "http://localhost:1234",
              null);

      assertEquals(200, response.statusCode());
      assertTrue(server.running());
    }
  }

  @Test
  void rejectsRetiredHandshakeSessionAndCancellationFlow() throws Exception {
    try (McpHttpServer server =
        McpHttpServer.start(registryWithEchoTool(), McpServerOptions.ephemeral("test"))) {
      HttpResponse<String> initialize = post(server.endpoint(), request(19, "initialize"));
      assertEquals(404, initialize.statusCode());
      assertEquals(-32_601, errorCode(initialize));

      JsonObject initialized = request(20, "notifications/initialized");
      initialized.remove("id");
      HttpResponse<String> initializedResponse = post(server.endpoint(), initialized);
      assertEquals(404, initializedResponse.statusCode());
      assertTrue(initializedResponse.body().isEmpty());

      JsonObject cancelled = request(21, "notifications/cancelled");
      cancelled.remove("id");
      HttpResponse<String> cancelledResponse = post(server.endpoint(), cancelled);
      assertEquals(404, cancelledResponse.statusCode());
      assertTrue(cancelledResponse.body().isEmpty());

      JsonObject list = request(22, "tools/list");
      HttpRequest sessionRequest =
          HttpRequest.newBuilder(server.endpoint())
              .timeout(Duration.ofSeconds(5))
              .header("Accept", "application/json, text/event-stream")
              .header("Content-Type", "application/json")
              .header("MCP-Protocol-Version", VERSION)
              .header("Mcp-Method", "tools/list")
              .header("Mcp-Session-Id", "retired-session")
              .POST(HttpRequest.BodyPublishers.ofString(list.toString()))
              .build();
      HttpResponse<String> sessionResponse =
          HttpClient.newHttpClient().send(sessionRequest, HttpResponse.BodyHandlers.ofString());
      assertEquals(400, sessionResponse.statusCode());
      assertEquals(-32_600, errorCode(sessionResponse));
      assertTrue(sessionResponse.headers().firstValue("Mcp-Session-Id").isEmpty());

      assertEquals(200, post(server.endpoint(), request(23, "tools/list")).statusCode());
      assertTrue(server.running());
    }
  }

  @Test
  void clientDisconnectDoesNotAffectServerAndCloseReleasesPort() throws Exception {
    ToolRegistry registry = registryWithEchoTool();
    McpHttpServer first = McpHttpServer.start(registry, McpServerOptions.ephemeral("test"));
    URI endpoint = first.endpoint();
    int port = endpoint.getPort();
    assertEquals(200, post(endpoint, request(16, "tools/list")).statusCode());
    assertEquals(200, post(endpoint, request(17, "tools/list")).statusCode());

    first.close();
    first.close();
    assertFalse(first.running());
    assertThrows(IOException.class, () -> post(endpoint, request(18, "tools/list")));

    McpServerOptions restartOptions =
        new McpServerOptions(
            InetAddress.getByName("127.0.0.1"),
            port,
            McpServerOptions.DEFAULT_MAX_REQUEST_BYTES,
            McpServerOptions.DEFAULT_MAX_CONCURRENT_REQUESTS,
            "Thread",
            "test");
    try (McpHttpServer restarted = McpHttpServer.start(registry, restartOptions)) {
      assertEquals(200, post(restarted.endpoint(), request(19, "tools/list")).statusCode());
    }
  }

  @Test
  void optionsRejectNonLoopbackListeners() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new McpServerOptions(
                InetAddress.getByName("0.0.0.0"), 1234, 1024, 8, "Thread", "test"));
    assertThrows(
        IllegalArgumentException.class,
        () -> McpServerOptions.configured("192.168.1.10", 1234, 1024, 8, "test"));
  }

  @Test
  void rejectsExcessConcurrentRequestsWithoutUnboundedWork() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ToolRegistry registry = new ToolRegistry();
    registry.register(new BlockingEchoTool(entered, release));
    McpServerOptions defaults = McpServerOptions.ephemeral("test");
    McpServerOptions singleRequest =
        new McpServerOptions(
            defaults.bindAddress(),
            0,
            defaults.maxRequestBytes(),
            1,
            defaults.serverName(),
            defaults.serverVersion());

    try (McpHttpServer server = McpHttpServer.start(registry, singleRequest)) {
      JsonObject firstCall = request(30, "tools/call");
      firstCall.getAsJsonObject("params").addProperty("name", "test.blocking_echo");
      firstCall.getAsJsonObject("params").add("arguments", object("message", "first"));
      CompletableFuture<HttpResponse<String>> first = postAsync(server.endpoint(), firstCall);
      assertTrue(entered.await(2, TimeUnit.SECONDS));

      JsonObject secondCall = request(31, "tools/call");
      secondCall.getAsJsonObject("params").addProperty("name", "test.blocking_echo");
      secondCall.getAsJsonObject("params").add("arguments", object("message", "second"));
      HttpResponse<String> busy = post(server.endpoint(), secondCall);

      assertEquals(503, busy.statusCode());
      release.countDown();
      assertEquals(200, first.get(2, TimeUnit.SECONDS).statusCode());
    } finally {
      release.countDown();
    }
  }

  private static ToolRegistry registryWithEchoTool() {
    ToolRegistry registry = new ToolRegistry();
    registry.register(echoTool("test.echo"));
    return registry;
  }

  private static JsonObject request(long id, String method) {
    return request(id, method, VERSION);
  }

  private static JsonObject request(long id, String method, String protocolVersion) {
    JsonObject metadata = new JsonObject();
    metadata.addProperty(McpJsonRpcHandler.PROTOCOL_VERSION_META, protocolVersion);
    metadata.add(McpJsonRpcHandler.CLIENT_CAPABILITIES_META, new JsonObject());
    JsonObject params = new JsonObject();
    params.add("_meta", metadata);
    JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("id", id);
    request.addProperty("method", method);
    request.add("params", params);
    return request;
  }

  private static JsonObject object(String name, String value) {
    JsonObject object = new JsonObject();
    object.addProperty(name, value);
    return object;
  }

  private static HttpResponse<String> post(URI endpoint, JsonObject request)
      throws IOException, InterruptedException {
    JsonObject params = request.getAsJsonObject("params");
    String method = request.get("method").getAsString();
    String name = params.has("name") ? params.get("name").getAsString() : null;
    String protocol =
        params.getAsJsonObject("_meta").get(McpJsonRpcHandler.PROTOCOL_VERSION_META).getAsString();
    return send(endpoint, request.toString(), protocol, method, name, null, null);
  }

  private static CompletableFuture<HttpResponse<String>> postAsync(
      URI endpoint, JsonObject request) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return post(endpoint, request);
          } catch (IOException | InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
          }
        });
  }

  private static HttpResponse<String> send(
      URI endpoint,
      String body,
      String protocolVersion,
      String method,
      String name,
      String origin,
      String contentType)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", contentType == null ? "application/json" : contentType)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (protocolVersion != null) {
      request.header("MCP-Protocol-Version", protocolVersion);
    }
    if (method != null) {
      request.header("Mcp-Method", method);
    }
    if (name != null) {
      request.header("Mcp-Name", name);
    }
    if (origin != null) {
      request.header("Origin", origin);
    }
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()
        .send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static JsonObject body(HttpResponse<String> response) {
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }

  private static int errorCode(HttpResponse<String> response) {
    return body(response).getAsJsonObject("error").get("code").getAsInt();
  }

  private static void assertServerInfo(JsonObject result, String version) {
    JsonObject serverInfo =
        result.getAsJsonObject("_meta").getAsJsonObject(McpJsonRpcHandler.SERVER_INFO_META);
    assertEquals("Thread", serverInfo.get("name").getAsString());
    assertEquals(version, serverInfo.get("version").getAsString());
  }

  private static final class BlockingEchoTool
      implements GameTool<TestJsonContracts.Message, TestJsonContracts.Message> {
    private final CountDownLatch entered;
    private final CountDownLatch release;
    private final JsonCodec<TestJsonContracts.Message> codec = TestJsonContracts.messageCodec();

    private BlockingEchoTool(CountDownLatch entered, CountDownLatch release) {
      this.entered = entered;
      this.release = release;
    }

    @Override
    public ToolId id() {
      return ToolId.of("test.blocking_echo");
    }

    @Override
    public String description() {
      return "Waits for the transport concurrency test to release it.";
    }

    @Override
    public JsonCodec<TestJsonContracts.Message> inputCodec() {
      return codec;
    }

    @Override
    public JsonCodec<TestJsonContracts.Message> outputCodec() {
      return codec;
    }

    @Override
    public ToolCapabilities capabilities() {
      return ToolCapabilities.alwaysAvailable();
    }

    @Override
    public ToolResult<TestJsonContracts.Message> execute(TestJsonContracts.Message input) {
      entered.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          return ToolResult.failure(
              ToolError.of(ToolErrorCode.TIMEOUT, "Test release did not arrive.", true));
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return ToolResult.failure(
            ToolError.of(ToolErrorCode.NOT_AVAILABLE, "Test request was interrupted.", true));
      }
      return ToolResult.success(input);
    }
  }
}
