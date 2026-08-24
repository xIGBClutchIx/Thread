package me.clutchy.thread.transport.mcp;

import com.google.gson.JsonObject;
import java.util.Objects;

record McpHttpResponse(int status, JsonObject body, boolean hasBody) {
  static McpHttpResponse json(int status, JsonObject body) {
    return new McpHttpResponse(status, Objects.requireNonNull(body, "body"), true);
  }

  static McpHttpResponse accepted() {
    return empty(202);
  }

  static McpHttpResponse empty(int status) {
    return new McpHttpResponse(status, null, false);
  }

  McpHttpResponse withStatus(int replacementStatus) {
    return new McpHttpResponse(replacementStatus, body, hasBody);
  }
}
