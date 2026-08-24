package me.clutchy.thread.core.integration;

import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import me.clutchy.thread.core.error.DuplicateRegistrationException;

/** Startup registry that activates integrations and reports them in deterministic ID order. */
public final class IntegrationRegistry {
  private final IntegrationContext context;
  private final SortedMap<IntegrationId, IntegrationInfo> integrations = new TreeMap<>();

  public IntegrationRegistry(IntegrationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  /** Installs an integration and rejects a duplicate before it can mutate the core registries. */
  public synchronized void register(GameIntegration integration) {
    Objects.requireNonNull(integration, "integration");
    IntegrationId id = Objects.requireNonNull(integration.id(), "integration.id()");
    if (integrations.containsKey(id)) {
      throw new DuplicateRegistrationException("integration", id.toString());
    }
    IntegrationInfo info = infoOf(integration);
    integration.register(context);
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
