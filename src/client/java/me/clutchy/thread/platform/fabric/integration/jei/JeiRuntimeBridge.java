package me.clutchy.thread.platform.fabric.integration.jei;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import mezz.jei.api.runtime.IJeiRuntime;

/** Package-private handoff from JEI's plugin lifecycle to Thread's discovered recipe provider. */
final class JeiRuntimeBridge {
  private static final AtomicReference<IJeiRuntime> RUNTIME = new AtomicReference<>();

  private JeiRuntimeBridge() {}

  static Optional<IJeiRuntime> runtime() {
    return Optional.ofNullable(RUNTIME.get());
  }

  static void available(IJeiRuntime runtime) {
    RUNTIME.set(Objects.requireNonNull(runtime, "runtime"));
  }

  static void unavailable() {
    RUNTIME.set(null);
  }
}
