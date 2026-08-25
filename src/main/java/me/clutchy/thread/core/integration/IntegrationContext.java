package me.clutchy.thread.core.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import me.clutchy.thread.core.context.ContextDescriptor;
import me.clutchy.thread.core.context.ContextProvider;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.error.DuplicateRegistrationException;
import me.clutchy.thread.core.integration.extension.CoreIntegrationExtensionPoints;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionPoint;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.integration.extension.IntegrationRecipeProvider;
import me.clutchy.thread.core.tool.GameTool;
import me.clutchy.thread.core.tool.ToolDescriptor;
import me.clutchy.thread.core.tool.ToolRegistry;

/**
 * Transactional contribution surface supplied to one integration during startup.
 *
 * <p>Tools and contexts use direct convenience methods. Recipe providers and platform-owned block,
 * block-entity, or entity enrichers use typed extension points. Integrations implement only the
 * contribution methods they need.
 */
public final class IntegrationContext {
  private final IntegrationId integrationId;
  private final ToolRegistry activeTools;
  private final ContextRegistry activeContexts;
  private final IntegrationExtensionRegistry activeExtensions;
  private final IntegrationRegistry activeIntegrations;
  private final ToolRegistry stagedToolValidation = new ToolRegistry();
  private final ContextRegistry stagedContextValidation = new ContextRegistry();
  private final List<GameTool<?, ?>> stagedTools = new ArrayList<>();
  private final List<ContextProvider<?>> stagedContexts = new ArrayList<>();
  private final List<PendingExtension<?>> stagedExtensions = new ArrayList<>();
  private final Map<String, Class<?>> stagedExtensionContracts = new TreeMap<>();
  private final Map<String, String> metadata = new TreeMap<>();
  private boolean open = true;

  IntegrationContext(
      IntegrationId integrationId,
      ToolRegistry activeTools,
      ContextRegistry activeContexts,
      IntegrationExtensionRegistry activeExtensions,
      IntegrationRegistry activeIntegrations) {
    this.integrationId = Objects.requireNonNull(integrationId, "integrationId");
    this.activeTools = Objects.requireNonNull(activeTools, "activeTools");
    this.activeContexts = Objects.requireNonNull(activeContexts, "activeContexts");
    this.activeExtensions = Objects.requireNonNull(activeExtensions, "activeExtensions");
    this.activeIntegrations = Objects.requireNonNull(activeIntegrations, "activeIntegrations");
  }

  /** Returns the stable ID of the integration currently contributing. */
  public IntegrationId integrationId() {
    return integrationId;
  }

  /** Stages one transport-independent read-only tool. */
  public void registerTool(GameTool<?, ?> tool) {
    requireOpen();
    stagedToolValidation.register(tool);
    stagedTools.add(tool);
  }

  /** Stages one bounded static or semi-static context provider. */
  public void registerContext(ContextProvider<?> contextProvider) {
    requireOpen();
    stagedContextValidation.register(contextProvider);
    stagedContexts.add(contextProvider);
  }

  /** Stages an additional recipe provider without replacing the guarded vanilla provider. */
  public void registerRecipeProvider(IntegrationRecipeProvider recipeProvider) {
    contribute(CoreIntegrationExtensionPoints.RECIPE_PROVIDER, recipeProvider);
  }

  /** Stages one typed core- or platform-owned extension contribution. */
  public <T> void contribute(IntegrationExtensionPoint<T> point, T contribution) {
    requireOpen();
    Objects.requireNonNull(point, "point");
    Objects.requireNonNull(contribution, "contribution");
    Class<?> previous = stagedExtensionContracts.putIfAbsent(point.id(), point.contract());
    if (previous != null && !previous.equals(point.contract())) {
      throw new IllegalArgumentException(
          "extension point ID is already staged with " + previous.getName());
    }
    activeExtensions.validate(point, contribution);
    stagedExtensions.add(new PendingExtension<>(point, contribution));
  }

  /**
   * Adds bounded integration-specific metadata to capability discovery.
   *
   * <p>Keys beginning with {@code thread.} are reserved for registry-generated metadata.
   */
  public void putMetadata(String key, String value) {
    requireOpen();
    String validatedKey = IntegrationInfo.validateMetadataKey(key);
    if (validatedKey.startsWith("thread.")) {
      throw new IllegalArgumentException("thread.* integration metadata keys are reserved");
    }
    String validatedValue = IntegrationInfo.validateMetadataValue(value);
    if (metadata.putIfAbsent(validatedKey, validatedValue) != null) {
      throw new DuplicateRegistrationException("integration metadata", validatedKey);
    }
  }

  /** Returns active tool descriptors; the view is resolved each time it is called. */
  public List<ToolDescriptor> activeTools() {
    return activeTools.descriptors();
  }

  /** Returns active context descriptors; the view is resolved each time it is called. */
  public List<ContextDescriptor> activeContexts() {
    return activeContexts.descriptors();
  }

  /** Returns active integration metadata in stable ID order. */
  public List<IntegrationInfo> activeIntegrations() {
    return activeIntegrations.integrations();
  }

  void prepareCommit() {
    requireOpen();
    open = false;
    for (ToolDescriptor descriptor : stagedToolValidation.descriptors()) {
      if (activeTools.contains(descriptor.id())) {
        throw new DuplicateRegistrationException("tool", descriptor.id().toString());
      }
    }
    for (ContextDescriptor descriptor : stagedContextValidation.descriptors()) {
      if (activeContexts.contains(descriptor.id())) {
        throw new DuplicateRegistrationException("context", descriptor.id().toString());
      }
    }
    stagedExtensions.forEach(PendingExtension::validateAgainst);
  }

  void commit() {
    if (open) {
      throw new IllegalStateException("integration context must be prepared before commit");
    }
    stagedTools.forEach(activeTools::register);
    stagedContexts.forEach(activeContexts::register);
    stagedExtensions.forEach(PendingExtension::commit);
  }

  Map<String, String> metadata(Map<String, String> hostMetadata) {
    TreeMap<String, String> combined = new TreeMap<>(metadata);
    hostMetadata.forEach(
        (key, value) -> {
          String validatedKey = IntegrationInfo.validateMetadataKey(key);
          String validatedValue = IntegrationInfo.validateMetadataValue(value);
          if (combined.putIfAbsent(validatedKey, validatedValue) != null) {
            throw new DuplicateRegistrationException("integration metadata", validatedKey);
          }
        });
    if (!stagedTools.isEmpty()) {
      combined.put("thread.tool_count", Integer.toString(stagedTools.size()));
    }
    if (!stagedContexts.isEmpty()) {
      combined.put("thread.context_count", Integer.toString(stagedContexts.size()));
    }
    if (!stagedExtensionContracts.isEmpty()) {
      combined.put("thread.extension_points", String.join(",", stagedExtensionContracts.keySet()));
    }
    return Map.copyOf(combined);
  }

  private void requireOpen() {
    if (!open) {
      throw new IllegalStateException("integration registration has already completed");
    }
  }

  private final class PendingExtension<T> {
    private final IntegrationExtensionPoint<T> point;
    private final T contribution;

    private PendingExtension(IntegrationExtensionPoint<T> point, T contribution) {
      this.point = point;
      this.contribution = contribution;
    }

    private void validateAgainst() {
      activeExtensions.validate(point, contribution);
    }

    private void commit() {
      activeExtensions.register(integrationId, point, contribution);
    }
  }
}
