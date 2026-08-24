package dev.xigbclutch.thread.core.integration;

import dev.xigbclutch.thread.core.context.ContextRegistry;
import dev.xigbclutch.thread.core.tool.ToolRegistry;
import java.util.Objects;

/** Core registries available while a game integration installs its extensions. */
public record IntegrationContext(ToolRegistry tools, ContextRegistry contexts) {
  public IntegrationContext {
    Objects.requireNonNull(tools, "tools");
    Objects.requireNonNull(contexts, "contexts");
  }
}
