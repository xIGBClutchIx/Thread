package me.clutchy.thread.core.tool;

import me.clutchy.thread.core.serialization.JsonCodec;

/**
 * Loader- and transport-independent unit of read-only game functionality.
 *
 * <p>Implementations expose explicit codecs so discovery schemas and runtime validation remain
 * identical across transports. {@link #execute(Object)} must return a structured failure for
 * expected availability or input problems; registries convert unexpected runtime exceptions into an
 * internal tool error.
 */
public interface GameTool<I, O> {
  /** Returns the globally stable, namespaced tool identifier. */
  ToolId id();

  /** Returns model-facing guidance describing when and how to use the tool. */
  String description();

  /** Returns the codec and schema used to validate untrusted invocation input. */
  JsonCodec<I> inputCodec();

  /** Returns the codec and schema used to validate and serialize successful output. */
  JsonCodec<O> outputCodec();

  /** Returns discovery metadata, including Thread's mandatory read-only declaration. */
  ToolCapabilities capabilities();

  /** Executes one invocation after registry input validation. */
  ToolResult<O> execute(I input);
}
