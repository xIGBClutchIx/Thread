package me.clutchy.thread.core.integration;

import java.util.Objects;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.tool.ToolRegistry;

/**
 * Core registries available while a game integration installs its extensions.
 *
 * @param tools registry receiving transport-independent tools
 * @param contexts registry receiving bounded context providers
 * @param integrations active integration registry, primarily for capability discovery
 */
public record IntegrationContext(
    ToolRegistry tools, ContextRegistry contexts, IntegrationRegistry integrations) {
  public IntegrationContext {
    Objects.requireNonNull(tools, "tools");
    Objects.requireNonNull(contexts, "contexts");
    Objects.requireNonNull(integrations, "integrations");
  }
}
