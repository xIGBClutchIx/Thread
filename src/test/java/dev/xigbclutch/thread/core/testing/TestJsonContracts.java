package dev.xigbclutch.thread.core.testing;

import dev.xigbclutch.thread.core.serialization.JsonCodec;
import dev.xigbclutch.thread.core.serialization.JsonSchema;
import dev.xigbclutch.thread.core.tool.GameTool;
import dev.xigbclutch.thread.core.tool.ToolCapabilities;
import dev.xigbclutch.thread.core.tool.ToolId;
import dev.xigbclutch.thread.core.tool.ToolResult;

public final class TestJsonContracts {
  private static final JsonSchema MESSAGE_SCHEMA =
      JsonSchema.parse(
          """
          {
            "type": "object",
            "properties": {
              "message": {"type": "string", "minLength": 1}
            },
            "required": ["message"],
            "additionalProperties": false
          }
          """);

  private TestJsonContracts() {}

  public static JsonCodec<Message> messageCodec() {
    return JsonCodec.of(Message.class, MESSAGE_SCHEMA);
  }

  public static EchoTool echoTool(String id) {
    return new EchoTool(ToolId.of(id));
  }

  public record Message(String message) {}

  public static final class EchoTool implements GameTool<Message, Message> {
    private final ToolId id;
    private final JsonCodec<Message> codec = messageCodec();

    private EchoTool(ToolId id) {
      this.id = id;
    }

    @Override
    public ToolId id() {
      return id;
    }

    @Override
    public String description() {
      return "Echoes a validated message.";
    }

    @Override
    public JsonCodec<Message> inputCodec() {
      return codec;
    }

    @Override
    public JsonCodec<Message> outputCodec() {
      return codec;
    }

    @Override
    public ToolCapabilities capabilities() {
      return ToolCapabilities.alwaysAvailable();
    }

    @Override
    public ToolResult<Message> execute(Message input) {
      return ToolResult.success(input);
    }
  }
}
