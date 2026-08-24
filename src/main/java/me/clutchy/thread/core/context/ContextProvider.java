package me.clutchy.thread.core.context;

import me.clutchy.thread.core.serialization.JsonCodec;
import me.clutchy.thread.core.tool.ToolResult;

/**
 * Supplies one bounded static or semi-static context value.
 *
 * <p>Dynamic game state belongs in a {@code GameTool}; context providers are intended for small
 * discovery-oriented snapshots that are safe to request independently of any transport.
 */
public interface ContextProvider<T> {
  /** Returns the globally stable, namespaced context identifier. */
  ContextId id();

  /** Returns client-facing guidance about the supplied context. */
  String description();

  /** Returns the codec and schema used to validate and serialize the supplied value. */
  JsonCodec<T> outputCodec();

  /** Reads one detached snapshot or returns a structured failure. */
  ToolResult<T> provide();
}
