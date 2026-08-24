package dev.xigbclutch.thread.core.context;

import static dev.xigbclutch.thread.core.testing.TestJsonContracts.messageCodec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import dev.xigbclutch.thread.core.error.DuplicateRegistrationException;
import dev.xigbclutch.thread.core.error.ToolErrorCode;
import dev.xigbclutch.thread.core.serialization.JsonCodec;
import dev.xigbclutch.thread.core.testing.TestJsonContracts.Message;
import dev.xigbclutch.thread.core.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextRegistryTest {
  @Test
  void registersDiscoversReadsAndSerializesContext() {
    ContextRegistry registry = new ContextRegistry();
    registry.register(new MessageContext("thread.zulu", "last"));
    registry.register(new MessageContext("thread.alpha", "first"));

    assertEquals(
        List.of(ContextId.of("thread.alpha"), ContextId.of("thread.zulu")),
        registry.descriptors().stream().map(ContextDescriptor::id).toList());
    ToolResult<JsonElement> result = registry.read(ContextId.of("thread.alpha"));
    assertTrue(result.successful());
    assertEquals("{\"message\":\"first\"}", result.value().toString());
  }

  @Test
  void rejectsDuplicateContextIdsDeterministically() {
    ContextRegistry registry = new ContextRegistry();
    registry.register(new MessageContext("thread.info", "first"));

    DuplicateRegistrationException exception =
        assertThrows(
            DuplicateRegistrationException.class,
            () -> registry.register(new MessageContext("thread.info", "second")));

    assertEquals("duplicate context ID: thread.info", exception.getMessage());
  }

  @Test
  void unknownContextAndProviderExceptionsAreStructured() {
    ContextRegistry registry = new ContextRegistry();
    registry.register(new ThrowingContext());

    assertEquals(
        ToolErrorCode.NOT_FOUND, registry.read(ContextId.of("thread.missing")).error().code());
    assertEquals(
        ToolErrorCode.INTERNAL_ERROR,
        registry.read(ContextId.of("thread.throwing")).error().code());
  }

  private record MessageContext(ContextId id, String value) implements ContextProvider<Message> {
    private MessageContext(String id, String value) {
      this(ContextId.of(id), value);
    }

    @Override
    public String description() {
      return "Test context.";
    }

    @Override
    public JsonCodec<Message> outputCodec() {
      return messageCodec();
    }

    @Override
    public ToolResult<Message> provide() {
      return ToolResult.success(new Message(value));
    }
  }

  private static final class ThrowingContext implements ContextProvider<Message> {
    @Override
    public ContextId id() {
      return ContextId.of("thread.throwing");
    }

    @Override
    public String description() {
      return "Throws unexpectedly.";
    }

    @Override
    public JsonCodec<Message> outputCodec() {
      return messageCodec();
    }

    @Override
    public ToolResult<Message> provide() {
      throw new IllegalStateException("boom");
    }
  }
}
