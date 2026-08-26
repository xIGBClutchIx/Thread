package me.clutchy.thread.platform.minecraft.threading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class MinecraftThreadExecutorTest {
  @Test
  void externalCallsAreMarshalledToTheOwningThread() throws Exception {
    try (ExecutorService owner = Executors.newSingleThreadExecutor()) {
      AtomicReference<Thread> owningThread = new AtomicReference<>();
      owner.submit(() -> owningThread.set(Thread.currentThread())).get();
      MinecraftThreadExecutor executor =
          new MinecraftThreadExecutor(
              () -> Thread.currentThread() == owningThread.get(),
              submitter(owner),
              Duration.ofSeconds(1));

      assertSame(owningThread.get(), executor.call(Thread::currentThread));
      assertThrows(
          IllegalStateException.class,
          () ->
              executor.call(
                  () -> {
                    throw new IllegalStateException("expected");
                  }));
    }
  }

  @Test
  void callsAlreadyOnTheOwningThreadRunInline() {
    AtomicInteger submissions = new AtomicInteger();
    MinecraftThreadExecutor executor =
        new MinecraftThreadExecutor(
            () -> true,
            new MinecraftThreadExecutor.TaskSubmitter() {
              @Override
              public <T> CompletableFuture<T> submit(Supplier<T> operation) {
                submissions.incrementAndGet();
                return CompletableFuture.completedFuture(operation.get());
              }
            },
            Duration.ofSeconds(1));

    assertEquals("inline", executor.call(() -> "inline"));
    assertEquals(0, submissions.get());
  }

  @Test
  void cancelsAndReturnsAControlledTimeoutWhenTheOwnerStalls() {
    AtomicReference<CompletableFuture<?>> submitted = new AtomicReference<>();
    MinecraftThreadExecutor executor =
        new MinecraftThreadExecutor(
            () -> false,
            new MinecraftThreadExecutor.TaskSubmitter() {
              @Override
              public <T> CompletableFuture<T> submit(Supplier<T> operation) {
                CompletableFuture<T> pending = new CompletableFuture<>();
                submitted.set(pending);
                return pending;
              }
            },
            Duration.ofMillis(20));

    GameThreadTimeoutException error =
        assertThrows(GameThreadTimeoutException.class, () -> executor.call(() -> "never"));

    assertEquals(20, error.timeout().toMillis());
    assertEquals(true, submitted.get().isCancelled());
  }

  @Test
  void normalizesTaskRejectionDuringWorldShutdown() {
    MinecraftThreadExecutor executor =
        new MinecraftThreadExecutor(
            () -> false,
            new MinecraftThreadExecutor.TaskSubmitter() {
              @Override
              public <T> CompletableFuture<T> submit(Supplier<T> operation) {
                throw new RejectedExecutionException("server stopped");
              }
            },
            Duration.ofSeconds(1));

    assertThrows(GameThreadExecutionException.class, () -> executor.call(() -> "unavailable"));
  }

  @Test
  void normalizesQueuedTaskRejectionWhenWorldUnloadsDuringARequest() {
    MinecraftThreadExecutor executor =
        new MinecraftThreadExecutor(
            () -> false,
            new MinecraftThreadExecutor.TaskSubmitter() {
              @Override
              public <T> CompletableFuture<T> submit(Supplier<T> operation) {
                return CompletableFuture.failedFuture(
                    new RejectedExecutionException("world unloaded"));
              }
            },
            Duration.ofSeconds(1));

    assertThrows(GameThreadExecutionException.class, () -> executor.call(() -> "unavailable"));
  }

  private static MinecraftThreadExecutor.TaskSubmitter submitter(ExecutorService executor) {
    return new MinecraftThreadExecutor.TaskSubmitter() {
      @Override
      public <T> CompletableFuture<T> submit(Supplier<T> operation) {
        return CompletableFuture.supplyAsync(operation, executor);
      }
    };
  }
}
