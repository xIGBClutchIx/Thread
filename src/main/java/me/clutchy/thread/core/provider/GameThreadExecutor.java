package me.clutchy.thread.core.provider;

import java.util.function.Supplier;

/**
 * Executes a state read on the logical game thread that owns the requested state.
 *
 * <p>Implementations may execute inline when already on the owning thread. Calls from external
 * threads block until the read completes so provider contracts remain synchronous and
 * transport-independent.
 */
public interface GameThreadExecutor {
  <T> T call(Supplier<T> operation);
}
