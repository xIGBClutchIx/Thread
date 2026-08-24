package me.clutchy.thread.core.integration;

import java.util.Objects;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.tool.ToolRegistry;

/** Core registries available while a game integration installs its extensions. */
public record IntegrationContext(ToolRegistry tools, ContextRegistry contexts) {
  public IntegrationContext {
    Objects.requireNonNull(tools, "tools");
    Objects.requireNonNull(contexts, "contexts");
  }
}
