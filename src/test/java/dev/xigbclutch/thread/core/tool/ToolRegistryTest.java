package dev.xigbclutch.thread.core.tool;

import static dev.xigbclutch.thread.core.testing.TestJsonContracts.echoTool;
import static dev.xigbclutch.thread.core.testing.TestJsonContracts.messageCodec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.xigbclutch.thread.core.error.DuplicateRegistrationException;
import dev.xigbclutch.thread.core.error.ToolError;
import dev.xigbclutch.thread.core.error.ToolErrorCode;
import dev.xigbclutch.thread.core.serialization.JsonCodec;
import dev.xigbclutch.thread.core.testing.TestJsonContracts.Message;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
  @Test
  void registersDiscoversInvokesAndSerializesWithoutMinecraftOrMcp() {
    ToolRegistry registry = new ToolRegistry();
    registry.register(echoTool("test.echo"));

    assertEquals(1, registry.descriptors().size());
    ToolDescriptor descriptor = registry.descriptors().getFirst();
    assertEquals(ToolId.of("test.echo"), descriptor.id());
    assertEquals(ToolAvailability.ALWAYS, descriptor.capabilities().availability());
    assertTrue(descriptor.capabilities().readOnly());

    ToolResult<JsonElement> result =
        registry.invoke("test.echo", JsonParser.parseString("{\"message\":\"hello\"}"));

    assertTrue(result.successful());
    assertEquals("{\"message\":\"hello\"}", result.value().toString());
  }

  @Test
  void discoveryOrderIsStableByToolId() {
    ToolRegistry registry = new ToolRegistry();
    registry.register(echoTool("test.zulu"));
    registry.register(echoTool("test.alpha"));

    assertEquals(
        java.util.List.of(ToolId.of("test.alpha"), ToolId.of("test.zulu")),
        registry.descriptors().stream().map(ToolDescriptor::id).toList());
  }

  @Test
  void duplicateToolIdsAreRejectedDeterministically() {
    ToolRegistry registry = new ToolRegistry();
    registry.register(echoTool("test.echo"));

    DuplicateRegistrationException exception =
        assertThrows(
            DuplicateRegistrationException.class, () -> registry.register(echoTool("test.echo")));

    assertEquals("duplicate tool ID: test.echo", exception.getMessage());
    assertEquals(1, registry.descriptors().size());
  }

  @Test
  void unknownToolInvocationReturnsStructuredError() {
    ToolResult<JsonElement> result =
        new ToolRegistry().invoke("test.missing", JsonParser.parseString("{}"));

    assertFalse(result.successful());
    assertEquals(ToolErrorCode.NOT_FOUND, result.error().code());
    assertEquals(Map.of("toolId", "test.missing"), result.error().details());
  }

  @Test
  void invalidInputReturnsSchemaPathAndReason() {
    ToolRegistry registry = new ToolRegistry();
    registry.register(echoTool("test.echo"));

    ToolResult<JsonElement> missing = registry.invoke("test.echo", JsonParser.parseString("{}"));
    ToolResult<JsonElement> extra =
        registry.invoke(
            "test.echo", JsonParser.parseString("{\"message\":\"hello\",\"extra\":true}"));

    assertEquals(ToolErrorCode.INVALID_INPUT, missing.error().code());
    assertEquals("$.message", missing.error().details().get("path"));
    assertEquals("required property is missing", missing.error().details().get("reason"));
    assertEquals(ToolErrorCode.INVALID_INPUT, extra.error().code());
    assertEquals("$.extra", extra.error().details().get("path"));
  }

  @Test
  void invalidTransportToolIdReturnsStructuredError() {
    ToolResult<JsonElement> result =
        new ToolRegistry().invoke("INVALID", JsonParser.parseString("{}"));

    assertEquals(ToolErrorCode.INVALID_INPUT, result.error().code());
    assertEquals("INVALID", result.error().details().get("toolId"));
  }

  @Test
  void toolFailuresPassThroughWithoutSerialization() {
    ToolError expected = ToolError.of(ToolErrorCode.NOT_AVAILABLE, "Not ready.", true);
    ToolRegistry registry = new ToolRegistry();
    registry.register(new FailingTool(expected));

    ToolResult<JsonElement> result =
        registry.invoke("test.failure", JsonParser.parseString("{\"message\":\"hello\"}"));

    assertEquals(expected, result.error());
  }

  @Test
  void unexpectedToolExceptionsBecomeStructuredInternalErrors() {
    ToolRegistry registry = new ToolRegistry();
    registry.register(new ThrowingTool());

    ToolResult<JsonElement> result =
        registry.invoke("test.throwing", JsonParser.parseString("{\"message\":\"hello\"}"));

    assertEquals(ToolErrorCode.INTERNAL_ERROR, result.error().code());
    assertEquals("test.throwing", result.error().details().get("toolId"));
  }

  @Test
  void mutableToolsAreRejectedFromV1Registry() {
    ToolRegistry registry = new ToolRegistry();

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> registry.register(new MutableTool()));

    assertEquals("V1 tools must be read-only: test.mutable", exception.getMessage());
  }

  private record FailingTool(ToolError failure) implements GameTool<Message, Message> {
    @Override
    public ToolId id() {
      return ToolId.of("test.failure");
    }

    @Override
    public String description() {
      return "Always fails.";
    }

    @Override
    public JsonCodec<Message> inputCodec() {
      return messageCodec();
    }

    @Override
    public JsonCodec<Message> outputCodec() {
      return messageCodec();
    }

    @Override
    public ToolCapabilities capabilities() {
      return ToolCapabilities.alwaysAvailable();
    }

    @Override
    public ToolResult<Message> execute(Message input) {
      return ToolResult.failure(failure);
    }
  }

  private static final class ThrowingTool implements GameTool<Message, Message> {
    @Override
    public ToolId id() {
      return ToolId.of("test.throwing");
    }

    @Override
    public String description() {
      return "Throws unexpectedly.";
    }

    @Override
    public JsonCodec<Message> inputCodec() {
      return messageCodec();
    }

    @Override
    public JsonCodec<Message> outputCodec() {
      return messageCodec();
    }

    @Override
    public ToolCapabilities capabilities() {
      return ToolCapabilities.alwaysAvailable();
    }

    @Override
    public ToolResult<Message> execute(Message input) {
      throw new IllegalStateException("boom");
    }
  }

  private static final class MutableTool implements GameTool<Message, Message> {
    @Override
    public ToolId id() {
      return ToolId.of("test.mutable");
    }

    @Override
    public String description() {
      return "Not valid in V1.";
    }

    @Override
    public JsonCodec<Message> inputCodec() {
      return messageCodec();
    }

    @Override
    public JsonCodec<Message> outputCodec() {
      return messageCodec();
    }

    @Override
    public ToolCapabilities capabilities() {
      return new ToolCapabilities(false, ToolAvailability.ALWAYS);
    }

    @Override
    public ToolResult<Message> execute(Message input) {
      return ToolResult.success(input);
    }
  }
}
