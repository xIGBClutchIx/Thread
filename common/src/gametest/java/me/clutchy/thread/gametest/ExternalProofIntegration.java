package me.clutchy.thread.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import me.clutchy.thread.core.integration.IntegrationContext;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegration;
import me.clutchy.thread.core.tool.ToolResult;

/** Shared test integration proving that every loader reaches the live contribution pipeline. */
public final class ExternalProofIntegration implements ThreadIntegration {
  private static final AtomicInteger RECIPE_LOOKUPS = new AtomicInteger();

  public ExternalProofIntegration() {}

  @Override
  public IntegrationId id() {
    return IntegrationId.of("gametest-bridge");
  }

  @Override
  public String version() {
    return "1.0.0";
  }

  @Override
  public String description() {
    return "Packaged proof of the external Thread integration bridge.";
  }

  @Override
  public void register(IntegrationContext context) {
    context.registerRecipeProvider(
        itemId -> {
          RECIPE_LOOKUPS.incrementAndGet();
          return ToolResult.success(List.of());
        });
    context.putMetadata("gametest.source", "external-loader-discovery");
  }

  /** Returns how many live recipe lookups reached this external contribution. */
  public static int recipeLookups() {
    return RECIPE_LOOKUPS.get();
  }
}
