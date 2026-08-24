package me.clutchy.thread.core.integration.vanilla;

import java.util.Objects;
import java.util.function.Function;
import me.clutchy.thread.core.serialization.JsonCodec;
import me.clutchy.thread.core.tool.GameTool;
import me.clutchy.thread.core.tool.ToolCapabilities;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.ToolResult;

final class FunctionalGameTool<I, O> implements GameTool<I, O> {
  private final ToolId id;
  private final String description;
  private final JsonCodec<I> inputCodec;
  private final JsonCodec<O> outputCodec;
  private final ToolCapabilities capabilities;
  private final Function<I, ToolResult<O>> operation;

  FunctionalGameTool(
      String id,
      String description,
      JsonCodec<I> inputCodec,
      JsonCodec<O> outputCodec,
      ToolCapabilities capabilities,
      Function<I, ToolResult<O>> operation) {
    this.id = ToolId.of(id);
    this.description = Objects.requireNonNull(description, "description");
    this.inputCodec = Objects.requireNonNull(inputCodec, "inputCodec");
    this.outputCodec = Objects.requireNonNull(outputCodec, "outputCodec");
    this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
    this.operation = Objects.requireNonNull(operation, "operation");
  }

  @Override
  public ToolId id() {
    return id;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public JsonCodec<I> inputCodec() {
    return inputCodec;
  }

  @Override
  public JsonCodec<O> outputCodec() {
    return outputCodec;
  }

  @Override
  public ToolCapabilities capabilities() {
    return capabilities;
  }

  @Override
  public ToolResult<O> execute(I input) {
    return operation.apply(input);
  }
}
