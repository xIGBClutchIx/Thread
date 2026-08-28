package me.clutchy.thread.gametest;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import me.clutchy.thread.core.integration.IntegrationContext;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegration;
import me.clutchy.thread.core.serialization.JsonCodec;
import me.clutchy.thread.core.serialization.JsonSchema;
import me.clutchy.thread.core.tool.GameTool;
import me.clutchy.thread.core.tool.ToolCapabilities;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.ToolResult;

/** Shared test integration proving that every loader reaches the live contribution pipeline. */
public final class ExternalProofIntegration implements ThreadIntegration {
  /** Tool ID used to prove that separately discovered integrations can publish MCP tools. */
  public static final String TOOL_ID = "gametest.integration_probe";

  private static final AtomicInteger RECIPE_LOOKUPS = new AtomicInteger();
  private static final JsonCodec<JsonObject> EMPTY_OBJECT_CODEC =
      JsonCodec.of(
          JsonObject.class,
          JsonSchema.parse(
              """
              {
                "type": "object",
                "properties": {},
                "required": [],
                "additionalProperties": false
              }
              """));
  private static final JsonCodec<JsonObject> PROBE_OUTPUT_CODEC =
      JsonCodec.of(
          JsonObject.class,
          JsonSchema.parse(
              """
              {
                "type": "object",
                "properties": {
                  "source": {"type": "string"}
                },
                "required": ["source"],
                "additionalProperties": false
              }
              """));
  private static final GameTool<JsonObject, JsonObject> PROBE_TOOL =
      new GameTool<>() {
        @Override
        public ToolId id() {
          return ToolId.of(TOOL_ID);
        }

        @Override
        public String description() {
          return "Confirms that an external Thread integration registered a read-only tool.";
        }

        @Override
        public JsonCodec<JsonObject> inputCodec() {
          return EMPTY_OBJECT_CODEC;
        }

        @Override
        public JsonCodec<JsonObject> outputCodec() {
          return PROBE_OUTPUT_CODEC;
        }

        @Override
        public ToolCapabilities capabilities() {
          return ToolCapabilities.alwaysAvailable();
        }

        @Override
        public ToolResult<JsonObject> execute(JsonObject input) {
          JsonObject output = new JsonObject();
          output.addProperty("source", "external-loader-discovery");
          return ToolResult.success(output);
        }
      };

  public ExternalProofIntegration() {}

  @Override
  public IntegrationId id() {
    return IntegrationId.of("gametest-bridge");
  }

  @Override
  public String version() {
    return "1.2.3";
  }

  @Override
  public String description() {
    return "Packaged proof of the external Thread integration bridge.";
  }

  @Override
  public void register(IntegrationContext context) {
    context.registerTool(PROBE_TOOL);
    context.registerRecipeProvider(
        itemId -> {
          RECIPE_LOOKUPS.incrementAndGet();
          return ToolResult.success(List.of());
        });
    context.putMetadata("gametest.source", "external-loader-discovery");
  }

  /** Returns how many live recipe lookups reached this external contribution. */
  public static int recipeLookups() {
    return RECIPE_LOOKUPS.get();
  }
}
