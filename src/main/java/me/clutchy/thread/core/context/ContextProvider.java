package me.clutchy.thread.core.context;

import me.clutchy.thread.core.serialization.JsonCodec;
import me.clutchy.thread.core.tool.ToolResult;

/** Supplies one bounded static or semi-static context value. */
public interface ContextProvider<T> {
  ContextId id();

  String description();

  JsonCodec<T> outputCodec();

  ToolResult<T> provide();
}
