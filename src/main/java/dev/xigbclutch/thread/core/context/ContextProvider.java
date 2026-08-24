package dev.xigbclutch.thread.core.context;

import dev.xigbclutch.thread.core.serialization.JsonCodec;
import dev.xigbclutch.thread.core.tool.ToolResult;

/** Supplies one bounded static or semi-static context value. */
public interface ContextProvider<T> {
  ContextId id();

  String description();

  JsonCodec<T> outputCodec();

  ToolResult<T> provide();
}
