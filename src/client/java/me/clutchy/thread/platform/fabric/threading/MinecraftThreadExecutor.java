package me.clutchy.thread.platform.fabric.threading;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

/** Marshals synchronous provider reads onto a Minecraft client or logical-server thread. */
public final class MinecraftThreadExecutor implements GameThreadExecutor {
  private final BooleanSupplier onOwningThread;
  private final TaskSubmitter submitter;

  MinecraftThreadExecutor(BooleanSupplier onOwningThread, TaskSubmitter submitter) {
    this.onOwningThread = Objects.requireNonNull(onOwningThread, "onOwningThread");
    this.submitter = Objects.requireNonNull(submitter, "submitter");
  }

  /** Creates an executor for state owned by the Minecraft client thread. */
  public static MinecraftThreadExecutor forClient(Minecraft client) {
    Objects.requireNonNull(client, "client");
    return new MinecraftThreadExecutor(client::isSameThread, client::submit);
  }

  /** Creates an executor for state owned by an integrated logical-server thread. */
  public static MinecraftThreadExecutor forServer(MinecraftServer server) {
    Objects.requireNonNull(server, "server");
    return new MinecraftThreadExecutor(server::isSameThread, server::submit);
  }

  @Override
  public <T> T call(Supplier<T> operation) {
    Objects.requireNonNull(operation, "operation");
    if (onOwningThread.getAsBoolean()) {
      return operation.get();
    }

    // Minecraft's own task queue establishes the logical-thread handoff; synchronized would only
    // serialize an unsafe read on the wrong thread.
    try {
      return submitter.submit(operation).get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new GameThreadExecutionException(
          "Interrupted while waiting for the game thread", exception);
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new GameThreadExecutionException("Game-thread read failed", cause);
    }
  }

  @FunctionalInterface
  interface TaskSubmitter {
    <T> CompletableFuture<T> submit(Supplier<T> operation);
  }
}
