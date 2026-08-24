package me.clutchy.thread.core.integration;

import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.error.DuplicateRegistrationException;
import me.clutchy.thread.core.tool.ToolRegistry;

/** Startup registry that activates integrations and reports them in deterministic ID order. */
public final class IntegrationRegistry {
  private final ToolRegistry tools;
  private final ContextRegistry contexts;
  private final SortedMap<IntegrationId, IntegrationInfo> integrations = new TreeMap<>();

  /** Creates an integration registry backed by the core registries it will extend. */
  public IntegrationRegistry(ToolRegistry tools, ContextRegistry contexts) {
    this.tools = Objects.requireNonNull(tools, "tools");
    this.contexts = Objects.requireNonNull(contexts, "contexts");
  }

  /** Installs an integration and rejects a duplicate before it can mutate the core registries. */
  public synchronized void register(GameIntegration integration) {
    Objects.requireNonNull(integration, "integration");
    IntegrationId id = Objects.requireNonNull(integration.id(), "integration.id()");
    if (integrations.containsKey(id)) {
      throw new DuplicateRegistrationException("integration", id.toString());
    }
    IntegrationInfo info = infoOf(integration);
    integration.register(new IntegrationContext(tools, contexts, this));
    integrations.put(id, info);
  }

  /** Returns active integration metadata sorted by stable identifier. */
  public synchronized List<IntegrationInfo> integrations() {
    return List.copyOf(integrations.values());
  }

  private static IntegrationInfo infoOf(GameIntegration integration) {
    return new IntegrationInfo(integration.id(), integration.version(), integration.description());
  }
}
