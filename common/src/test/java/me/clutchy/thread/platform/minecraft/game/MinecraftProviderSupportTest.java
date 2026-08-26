package me.clutchy.thread.platform.minecraft.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.function.Supplier;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.minecraft.threading.GameThreadExecutionException;
import me.clutchy.thread.platform.minecraft.threading.GameThreadTimeoutException;
import org.junit.jupiter.api.Test;

class MinecraftProviderSupportTest {
  @Test
  void exposesTimeoutsAsRetryableStructuredErrors() {
    ToolResult<String> result =
        MinecraftProviderSupport.read(
            failing(new GameThreadTimeoutException(Duration.ofMillis(25))),
            "get_player",
            () -> ToolResult.success("unused"));

    assertFalse(result.successful());
    assertEquals(ToolErrorCode.TIMEOUT, result.error().code());
    assertTrue(result.error().retryable());
    assertEquals("25", result.error().details().get("timeoutMillis"));
  }

  @Test
  void exposesLifecycleDispatchRejectionAsRetryableUnavailability() {
    ToolResult<String> result =
        MinecraftProviderSupport.read(
            failing(new GameThreadExecutionException("world stopped")),
            "get_inventory",
            () -> ToolResult.success("unused"));

    assertFalse(result.successful());
    assertEquals(ToolErrorCode.NOT_AVAILABLE, result.error().code());
    assertTrue(result.error().retryable());
  }

  private static GameThreadExecutor failing(RuntimeException failure) {
    return new GameThreadExecutor() {
      @Override
      public <T> T call(Supplier<T> operation) {
        throw failure;
      }
    };
  }
}
