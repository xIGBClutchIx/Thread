package me.clutchy.thread.transport.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.serialization.ThreadJson;
import me.clutchy.thread.core.tool.ToolDescriptor;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.core.tool.ToolResult;

final class McpJsonRpcHandler {
  static final String PROTOCOL_VERSION = "2026-07-28";
  static final String INITIALIZATION_PROTOCOL_VERSION = "2025-11-25";
  static final String SERVER_INFO_META = "io.modelcontextprotocol/serverInfo";
  static final int UNSUPPORTED_PROTOCOL_VERSION = -32_022;

  private static final int PARSE_ERROR = -32_700;
  private static final int INVALID_REQUEST = -32_600;
  private static final int METHOD_NOT_FOUND = -32_601;
  private static final int INVALID_PARAMS = -32_602;
  private static final int INTERNAL_ERROR = -32_603;
  private static final long DISCOVERY_TTL_MILLIS = 60_000;
  private static final List<String> SUPPORTED_PROTOCOL_VERSIONS =
      List.of(PROTOCOL_VERSION, INITIALIZATION_PROTOCOL_VERSION);
  private static final String INSTRUCTIONS =
      "Use Thread's read-only tools for live Minecraft state. Check minecraft.get_status before "
          + "gameplay tools; V1 gameplay access supports single-player only.";

  private final ToolRegistry tools;
  private final Gson gson = ThreadJson.create();
  private final JsonObject serverInfo;

  McpJsonRpcHandler(ToolRegistry tools, McpServerOptions options) {
    this.tools = Objects.requireNonNull(tools, "tools");
    Objects.requireNonNull(options, "options");
    serverInfo = new JsonObject();
    serverInfo.addProperty("name", options.serverName());
    serverInfo.addProperty("version", options.serverVersion());
  }

  McpHttpResponse handle(JsonObject request, String protocolVersionHeader) {
    JsonElement id = requestId(request);
    if (!validRequestEnvelope(request)) {
      return error(400, id, INVALID_REQUEST, "Invalid Request", null);
    }

    String method = request.get("method").getAsString();
    JsonObject params =
        request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
    if (method.equals("initialize")) {
      if (!request.has("id")) {
        return error(400, id, INVALID_REQUEST, "initialize must be a request", null);
      }
      return initialize(id, params);
    }

    McpHttpResponse protocolFailure = validateProtocolVersion(id, protocolVersionHeader);
    if (protocolFailure != null) {
      return protocolFailure;
    }

    if (!request.has("id")) {
      return method.equals("notifications/initialized")
          ? McpHttpResponse.empty(202)
          : McpHttpResponse.empty(404);
    }
    return switch (method) {
      case "server/discover" -> success(id, discoveryResult());
      case "tools/list" -> success(id, listToolsResult());
      case "tools/call" -> callTool(id, params);
      default -> error(404, id, METHOD_NOT_FOUND, "Method not found", null);
    };
  }

  McpHttpResponse parseError() {
    return error(400, JsonNull.INSTANCE, PARSE_ERROR, "Parse error", null);
  }

  McpHttpResponse invalidRequest(String message) {
    return error(400, JsonNull.INSTANCE, INVALID_REQUEST, message, null);
  }

  McpHttpResponse internalError(JsonElement id) {
    return error(500, id, INTERNAL_ERROR, "Internal error", null);
  }

  private McpHttpResponse initialize(JsonElement id, JsonObject params) {
    String requestedVersion = optionalString(params, "protocolVersion");
    if (requestedVersion == null) {
      return error(400, id, INVALID_PARAMS, "params.protocolVersion must be a string", null);
    }
    if (!params.has("capabilities") || !params.get("capabilities").isJsonObject()) {
      return error(400, id, INVALID_PARAMS, "params.capabilities must be an object", null);
    }
    if (!params.has("clientInfo") || !params.get("clientInfo").isJsonObject()) {
      return error(400, id, INVALID_PARAMS, "params.clientInfo must be an object", null);
    }

    String negotiatedVersion =
        SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)
            ? requestedVersion
            : PROTOCOL_VERSION;
    return success(id, initializationResult(negotiatedVersion));
  }

  private McpHttpResponse validateProtocolVersion(JsonElement id, String protocolVersionHeader) {
    if (protocolVersionHeader == null || protocolVersionHeader.isBlank()) {
      return error(
          400,
          id,
          INVALID_REQUEST,
          "MCP-Protocol-Version header is required after initialization",
          null);
    }
    if (!SUPPORTED_PROTOCOL_VERSIONS.contains(protocolVersionHeader)) {
      JsonObject data = new JsonObject();
      data.addProperty("requested", protocolVersionHeader);
      JsonArray supported = new JsonArray();
      SUPPORTED_PROTOCOL_VERSIONS.forEach(supported::add);
      data.add("supported", supported);
      return error(400, id, UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", data);
    }
    return null;
  }

  private JsonObject initializationResult(String negotiatedVersion) {
    JsonObject result = new JsonObject();
    result.addProperty("protocolVersion", negotiatedVersion);
    JsonObject capabilities = new JsonObject();
    JsonObject toolCapabilities = new JsonObject();
    toolCapabilities.addProperty("listChanged", false);
    capabilities.add("tools", toolCapabilities);
    result.add("capabilities", capabilities);
    result.add("serverInfo", serverInfo.deepCopy());
    result.addProperty("instructions", INSTRUCTIONS);
    return result;
  }

  private JsonObject discoveryResult() {
    JsonObject result = baseResult();
    result.addProperty("cacheScope", "public");
    JsonObject capabilities = new JsonObject();
    JsonObject toolCapabilities = new JsonObject();
    toolCapabilities.addProperty("listChanged", false);
    capabilities.add("tools", toolCapabilities);
    result.add("capabilities", capabilities);
    result.addProperty("instructions", INSTRUCTIONS);
    JsonArray supported = new JsonArray();
    SUPPORTED_PROTOCOL_VERSIONS.forEach(supported::add);
    result.add("supportedVersions", supported);
    result.addProperty("ttlMs", DISCOVERY_TTL_MILLIS);
    return result;
  }

  private JsonObject listToolsResult() {
    JsonObject result = baseResult();
    result.addProperty("cacheScope", "public");
    JsonArray catalog = new JsonArray();
    for (ToolDescriptor descriptor : tools.descriptors()) {
      JsonObject tool = new JsonObject();
      tool.addProperty("name", descriptor.id().toString());
      tool.addProperty("description", descriptor.description());
      tool.add("inputSchema", descriptor.inputSchema().document());
      tool.add("outputSchema", descriptor.outputSchema().document());
      JsonObject annotations = new JsonObject();
      annotations.addProperty("readOnlyHint", descriptor.capabilities().readOnly());
      annotations.addProperty("destructiveHint", false);
      annotations.addProperty("idempotentHint", true);
      annotations.addProperty("openWorldHint", false);
      tool.add("annotations", annotations);
      JsonObject metadata = new JsonObject();
      metadata.addProperty(
          "me.clutchy.thread/availability", descriptor.capabilities().availability().name());
      tool.add("_meta", metadata);
      catalog.add(tool);
    }
    result.add("tools", catalog);
    result.addProperty("ttlMs", DISCOVERY_TTL_MILLIS);
    return result;
  }

  private McpHttpResponse callTool(JsonElement id, JsonObject params) {
    String name = optionalString(params, "name");
    if (name == null) {
      return error(400, id, INVALID_PARAMS, "params.name must be a string", null);
    }
    boolean registered =
        tools.descriptors().stream()
            .anyMatch(descriptor -> descriptor.id().toString().equals(name));
    if (!registered) {
      JsonObject data = new JsonObject();
      data.addProperty("name", name);
      return error(400, id, INVALID_PARAMS, "Unknown tool", data);
    }

    JsonElement arguments = params.has("arguments") ? params.get("arguments") : new JsonObject();
    if (!arguments.isJsonObject()) {
      return error(400, id, INVALID_PARAMS, "params.arguments must be an object", null);
    }
    ToolResult<JsonElement> invocation = tools.invoke(name, arguments);
    return success(id, toolResult(invocation));
  }

  private JsonObject toolResult(ToolResult<JsonElement> invocation) {
    JsonObject result = baseResult();
    JsonElement structured;
    if (invocation.successful()) {
      structured = invocation.value().deepCopy();
      result.addProperty("isError", false);
    } else {
      structured = toolError(invocation.error());
      result.addProperty("isError", true);
    }
    JsonObject content = new JsonObject();
    content.addProperty("type", "text");
    content.addProperty("text", gson.toJson(structured));
    JsonArray blocks = new JsonArray();
    blocks.add(content);
    result.add("content", blocks);
    result.add("structuredContent", structured);
    return result;
  }

  private JsonObject toolError(ToolError error) {
    JsonObject structured = new JsonObject();
    structured.addProperty("code", error.code().name());
    structured.addProperty("message", error.message());
    structured.addProperty("retryable", error.retryable());
    JsonObject details = new JsonObject();
    for (Map.Entry<String, String> detail : error.details().entrySet()) {
      details.addProperty(detail.getKey(), detail.getValue());
    }
    structured.add("details", details);
    return structured;
  }

  private JsonObject baseResult() {
    JsonObject result = new JsonObject();
    result.addProperty("resultType", "complete");
    JsonObject metadata = new JsonObject();
    metadata.add(SERVER_INFO_META, serverInfo.deepCopy());
    result.add("_meta", metadata);
    return result;
  }

  private McpHttpResponse success(JsonElement id, JsonObject result) {
    JsonObject response = new JsonObject();
    response.addProperty("jsonrpc", "2.0");
    response.add("id", id.deepCopy());
    response.add("result", result);
    return McpHttpResponse.json(200, response);
  }

  private McpHttpResponse error(
      int status, JsonElement id, int code, String message, JsonObject data) {
    JsonObject error = new JsonObject();
    error.addProperty("code", code);
    error.addProperty("message", message);
    if (data != null) {
      error.add("data", data);
    }
    JsonObject response = new JsonObject();
    response.addProperty("jsonrpc", "2.0");
    response.add("id", id == null ? JsonNull.INSTANCE : id.deepCopy());
    response.add("error", error);
    return McpHttpResponse.json(status, response);
  }

  private static boolean validRequestEnvelope(JsonObject request) {
    if (!request.has("jsonrpc")
        || !request.get("jsonrpc").isJsonPrimitive()
        || !request.getAsJsonPrimitive("jsonrpc").isString()
        || !request.get("jsonrpc").getAsString().equals("2.0")) {
      return false;
    }
    if (!request.has("method")
        || !request.get("method").isJsonPrimitive()
        || !request.getAsJsonPrimitive("method").isString()
        || request.get("method").getAsString().isBlank()) {
      return false;
    }
    if (request.has("params") && !request.get("params").isJsonObject()) {
      return false;
    }
    return !request.has("id") || validId(request.get("id"));
  }

  private static JsonElement requestId(JsonObject request) {
    JsonElement id = request.get("id");
    return validId(id) ? id.deepCopy() : JsonNull.INSTANCE;
  }

  private static boolean validId(JsonElement id) {
    if (id == null || !id.isJsonPrimitive()) {
      return false;
    }
    JsonPrimitive primitive = id.getAsJsonPrimitive();
    if (primitive.isString()) {
      return true;
    }
    if (!primitive.isNumber()) {
      return false;
    }
    try {
      BigDecimal number = primitive.getAsBigDecimal();
      return number.stripTrailingZeros().scale() <= 0;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private static String optionalString(JsonObject object, String name) {
    if (!object.has(name)
        || !object.get(name).isJsonPrimitive()
        || !object.getAsJsonPrimitive(name).isString()) {
      return null;
    }
    return object.get(name).getAsString();
  }
}
