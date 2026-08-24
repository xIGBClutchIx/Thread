package me.clutchy.thread.platform.fabric.threading;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/** Marshals synchronous provider reads onto a Minecraft client or logical-server thread. */
public final class MinecraftThreadExecutor implements GameThreadExecutor {
  private final BooleanSupplier onOwningThread;
  private final TaskSubmitter submitter;
  private final Duration timeout;

  MinecraftThreadExecutor(
      BooleanSupplier onOwningThread, TaskSubmitter submitter, Duration timeout) {
    this.onOwningThread = Objects.requireNonNull(onOwningThread, "onOwningThread");
    this.submitter = Objects.requireNonNull(submitter, "submitter");
    this.timeout = requirePositive(timeout);
  }

  /** Creates a client-thread executor with an explicit dispatch timeout. */
  public static MinecraftThreadExecutor forClient(Minecraft client, Duration timeout) {
    Objects.requireNonNull(client, "client");
    return new MinecraftThreadExecutor(client::isSameThread, client::submit, timeout);
  }

  /** Creates an integrated-server executor with an explicit dispatch timeout. */
  public static MinecraftThreadExecutor forServer(MinecraftServer server, Duration timeout) {
    Objects.requireNonNull(server, "server");
    return new MinecraftThreadExecutor(server::isSameThread, server::submit, timeout);
  }

  @Override
  public <T> T call(Supplier<T> operation) {
    Objects.requireNonNull(operation, "operation");
    if (onOwningThread.getAsBoolean()) {
      return operation.get();
    }

    // Minecraft's own task queue establishes the logical-thread handoff; synchronized would only
    // serialize an unsafe read on the wrong thread.
    CompletableFuture<T> task;
    try {
      task = Objects.requireNonNull(submitter.submit(operation), "submitted task");
    } catch (RuntimeException exception) {
      throw new GameThreadExecutionException("Game-thread read could not be queued", exception);
    }
    try {
      return task.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException exception) {
      task.cancel(false);
      Thread.currentThread().interrupt();
      throw new GameThreadExecutionException(
          "Interrupted while waiting for the game thread", exception);
    } catch (TimeoutException exception) {
      task.cancel(false);
      throw new GameThreadTimeoutException(timeout);
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RejectedExecutionException rejected) {
        throw new GameThreadExecutionException(
            "Game-thread read was rejected during execution", rejected);
      }
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new GameThreadExecutionException("Game-thread read failed", cause);
    }
  }

  private static Duration requirePositive(Duration timeout) {
    Objects.requireNonNull(timeout, "timeout");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    return timeout;
  }

  @FunctionalInterface
  interface TaskSubmitter {
    <T> CompletableFuture<T> submit(Supplier<T> operation);
  }
}
