package me.clutchy.thread.core.tool;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import me.clutchy.thread.core.error.DuplicateRegistrationException;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.serialization.JsonCodec;

/** Deterministic registry and transport-neutral invocation boundary for game tools. */
public final class ToolRegistry {
  private static final System.Logger LOGGER = System.getLogger(ToolRegistry.class.getName());

  private final ConcurrentSkipListMap<ToolId, RegisteredTool<?, ?>> tools =
      new ConcurrentSkipListMap<>();

  /**
   * Registers one tool, rejecting duplicate IDs and non-read-only capabilities.
   *
   * @throws me.clutchy.thread.core.error.DuplicateRegistrationException when the ID is registered
   *     already
   * @throws IllegalArgumentException when the tool is not read-only
   */
  public void register(GameTool<?, ?> tool) {
    Objects.requireNonNull(tool, "tool");
    RegisteredTool<?, ?> registered = registeredTool(tool);
    if (tools.putIfAbsent(registered.descriptor().id(), registered) != null) {
      throw new DuplicateRegistrationException("tool", registered.descriptor().id().toString());
    }
  }

  /** Returns discovery metadata sorted by stable tool ID. */
  public List<ToolDescriptor> descriptors() {
    return tools.values().stream().map(RegisteredTool::descriptor).toList();
  }

  /** Returns whether an exact validated tool ID is registered. */
  public boolean contains(ToolId toolId) {
    return tools.containsKey(Objects.requireNonNull(toolId, "toolId"));
  }

  /**
   * Invokes a tool from an untrusted transport-provided identifier.
   *
   * <p>Invalid or unknown identifiers and codec failures are returned as structured results; they
   * do not escape as argument exceptions.
   */
  public ToolResult<JsonElement> invoke(String toolId, JsonElement input) {
    ToolId parsedId;
    try {
      parsedId = ToolId.of(toolId);
    } catch (IllegalArgumentException | NullPointerException exception) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.INVALID_INPUT,
              "Tool ID is invalid.",
              false,
              Map.of("toolId", String.valueOf(toolId))));
    }
    return invoke(parsedId, input);
  }

  /** Invokes a registered tool by validated identifier. */
  public ToolResult<JsonElement> invoke(ToolId toolId, JsonElement input) {
    Objects.requireNonNull(toolId, "toolId");
    RegisteredTool<?, ?> registered = tools.get(toolId);
    if (registered == null) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.NOT_FOUND,
              "No tool is registered with that ID.",
              false,
              Map.of("toolId", toolId.toString())));
    }
    return invokeTyped(registered, input);
  }

  private static <I, O> RegisteredTool<I, O> registeredTool(GameTool<I, O> tool) {
    ToolId id = Objects.requireNonNull(tool.id(), "tool.id()");
    JsonCodec<I> inputCodec = Objects.requireNonNull(tool.inputCodec(), "tool.inputCodec()");
    JsonCodec<O> outputCodec = Objects.requireNonNull(tool.outputCodec(), "tool.outputCodec()");
    ToolCapabilities capabilities =
        Objects.requireNonNull(tool.capabilities(), "tool.capabilities()");
    if (!capabilities.readOnly()) {
      throw new IllegalArgumentException("Thread tools must be read-only: " + id);
    }
    ToolDescriptor descriptor =
        new ToolDescriptor(
            id, tool.description(), inputCodec.schema(), outputCodec.schema(), capabilities);
    return new RegisteredTool<>(tool, descriptor, inputCodec, outputCodec);
  }

  private static <I, O> ToolResult<JsonElement> invokeTyped(
      RegisteredTool<I, O> registered, JsonElement input) {
    ToolResult<I> decoded = registered.inputCodec().decode(input);
    if (!decoded.successful()) {
      return ToolResult.failure(decoded.error());
    }

    ToolResult<O> executed;
    try {
      executed = Objects.requireNonNull(registered.tool().execute(decoded.value()), "tool result");
    } catch (RuntimeException exception) {
      LOGGER.log(
          System.Logger.Level.ERROR,
          "Tool {0} failed unexpectedly ({1})",
          registered.descriptor().id(),
          exception.getClass().getName());
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.INTERNAL_ERROR,
              "Tool execution failed unexpectedly.",
              false,
              Map.of("toolId", registered.descriptor().id().toString())));
    }
    if (!executed.successful()) {
      LOGGER.log(
          System.Logger.Level.DEBUG,
          "Tool {0} returned controlled error {1}",
          registered.descriptor().id(),
          Objects.requireNonNull(executed.error()).code());
      return ToolResult.failure(executed.error());
    }
    return registered.outputCodec().encode(executed.value());
  }

  private record RegisteredTool<I, O>(
      GameTool<I, O> tool,
      ToolDescriptor descriptor,
      JsonCodec<I> inputCodec,
      JsonCodec<O> outputCodec) {}
}
