package dev.xigbclutch.thread.core.tool;

import dev.xigbclutch.thread.core.serialization.JsonCodec;

/** Loader- and transport-independent unit of read-only game functionality. */
public interface GameTool<I, O> {
  ToolId id();

  String description();

  JsonCodec<I> inputCodec();

  JsonCodec<O> outputCodec();

  ToolCapabilities capabilities();

  ToolResult<O> execute(I input);
}
