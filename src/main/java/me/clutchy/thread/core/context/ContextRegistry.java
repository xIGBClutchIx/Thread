package me.clutchy.thread.core.context;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import me.clutchy.thread.core.error.DuplicateRegistrationException;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.serialization.JsonCodec;
import me.clutchy.thread.core.tool.ToolResult;

/** Deterministic registry for bounded static and semi-static context providers. */
public final class ContextRegistry {
  private final ConcurrentSkipListMap<ContextId, RegisteredContext<?>> providers =
      new ConcurrentSkipListMap<>();

  /**
   * Registers a context provider and rejects duplicate identifiers.
   *
   * @throws me.clutchy.thread.core.error.DuplicateRegistrationException when the ID is registered
   *     already
   */
  public void register(ContextProvider<?> provider) {
    Objects.requireNonNull(provider, "provider");
    RegisteredContext<?> registered = registeredContext(provider);
    if (providers.putIfAbsent(registered.descriptor().id(), registered) != null) {
      throw new DuplicateRegistrationException("context", registered.descriptor().id().toString());
    }
  }

  /** Returns context discovery metadata sorted by stable identifier. */
  public List<ContextDescriptor> descriptors() {
    return providers.values().stream().map(RegisteredContext::descriptor).toList();
  }

  /** Returns whether an exact validated context ID is registered. */
  public boolean contains(ContextId contextId) {
    return providers.containsKey(Objects.requireNonNull(contextId, "contextId"));
  }

  /** Reads and serializes one context value. */
  public ToolResult<JsonElement> read(ContextId contextId) {
    Objects.requireNonNull(contextId, "contextId");
    RegisteredContext<?> registered = providers.get(contextId);
    if (registered == null) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.NOT_FOUND,
              "No context provider is registered with that ID.",
              false,
              Map.of("contextId", contextId.toString())));
    }
    return readTyped(registered);
  }

  private static <T> RegisteredContext<T> registeredContext(ContextProvider<T> provider) {
    ContextId id = Objects.requireNonNull(provider.id(), "provider.id()");
    JsonCodec<T> outputCodec =
        Objects.requireNonNull(provider.outputCodec(), "provider.outputCodec()");
    ContextDescriptor descriptor =
        new ContextDescriptor(id, provider.description(), outputCodec.schema());
    return new RegisteredContext<>(provider, descriptor, outputCodec);
  }

  private static <T> ToolResult<JsonElement> readTyped(RegisteredContext<T> registered) {
    ToolResult<T> provided;
    try {
      provided = Objects.requireNonNull(registered.provider().provide(), "context result");
    } catch (RuntimeException exception) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.INTERNAL_ERROR,
              "Context provider failed unexpectedly.",
              false,
              Map.of("contextId", registered.descriptor().id().toString())));
    }
    if (!provided.successful()) {
      return ToolResult.failure(provided.error());
    }
    return registered.outputCodec().encode(provided.value());
  }

  private record RegisteredContext<T>(
      ContextProvider<T> provider, ContextDescriptor descriptor, JsonCodec<T> outputCodec) {}
}
