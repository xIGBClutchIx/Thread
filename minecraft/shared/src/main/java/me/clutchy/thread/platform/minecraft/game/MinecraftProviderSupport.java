package me.clutchy.thread.platform.minecraft.game;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.minecraft.threading.GameThreadExecutionException;
import me.clutchy.thread.platform.minecraft.threading.GameThreadTimeoutException;

/** Shared failure normalization for Minecraft-backed provider reads. */
public final class MinecraftProviderSupport {
  private MinecraftProviderSupport() {}

  /** Executes a provider read and converts dispatch/runtime failures into a stable core error. */
  public static <T> ToolResult<T> read(
      GameThreadExecutor executor, String operation, Supplier<ToolResult<T>> read) {
    Objects.requireNonNull(executor, "executor");
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(read, "read");
    try {
      return Objects.requireNonNull(executor.call(read), "provider result");
    } catch (GameThreadTimeoutException exception) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.TIMEOUT,
              "Minecraft did not complete the state read before its deadline.",
              true,
              Map.of(
                  "operation",
                  operation,
                  "timeoutMillis",
                  Long.toString(exception.timeout().toMillis()))));
    } catch (GameThreadExecutionException exception) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.NOT_AVAILABLE,
              "Minecraft state changed before the read could be completed.",
              true,
              Map.of("operation", operation)));
    } catch (RuntimeException exception) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.INTERNAL_ERROR,
              "Minecraft state could not be read safely.",
              true,
              Map.of("operation", operation)));
    }
  }
}
