package me.clutchy.thread.core.integration;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.error.DuplicateRegistrationException;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.capability.CapabilitiesSnapshot;
import me.clutchy.thread.core.model.capability.IntegrationCapability;
import me.clutchy.thread.core.model.capability.IntegrationMetadataEntry;
import me.clutchy.thread.core.tool.ToolRegistry;

/**
 * Startup registry that activates integrations transactionally and reports active metadata in
 * deterministic ID order.
 */
public final class IntegrationRegistry {
  private static final System.Logger LOGGER = System.getLogger(IntegrationRegistry.class.getName());

  private final ToolRegistry tools;
  private final ContextRegistry contexts;
  private final IntegrationExtensionRegistry extensions;
  private final SortedMap<IntegrationId, IntegrationInfo> integrations = new TreeMap<>();

  /** Creates an integration registry backed by the core and typed extension registries. */
  public IntegrationRegistry(
      ToolRegistry tools, ContextRegistry contexts, IntegrationExtensionRegistry extensions) {
    this.tools = Objects.requireNonNull(tools, "tools");
    this.contexts = Objects.requireNonNull(contexts, "contexts");
    this.extensions = Objects.requireNonNull(extensions, "extensions");
  }

  /**
   * Installs a required integration and rejects a duplicate before its callback can run.
   *
   * <p>Use {@link #discover} for optional integrations whose absence or linkage failure must not
   * stop startup.
   */
  public synchronized void register(ThreadIntegration integration) {
    register(integration, Map.of());
  }

  /**
   * Evaluates optional candidates in stable ID order and isolates every rejected or failed entry.
   *
   * <p>The loader is invoked only after the candidate is enabled, the target mod is present, and
   * its version is compatible. Duplicate candidate IDs are rejected before any duplicate class is
   * resolved.
   */
  public synchronized List<IntegrationActivation> discover(
      List<IntegrationCandidate> candidates,
      IntegrationEnvironment environment,
      Predicate<IntegrationId> enabled,
      IntegrationLoader loader) {
    Objects.requireNonNull(candidates, "candidates");
    Objects.requireNonNull(environment, "environment");
    Objects.requireNonNull(enabled, "enabled");
    Objects.requireNonNull(loader, "loader");

    List<IntegrationCandidate> ordered =
        candidates.stream()
            .map(candidate -> Objects.requireNonNull(candidate, "candidate"))
            .sorted(
                Comparator.comparing(IntegrationCandidate::id)
                    .thenComparing(IntegrationCandidate::targetModId)
                    .thenComparing(IntegrationCandidate::implementationClassName))
            .toList();
    Map<IntegrationId, Integer> candidateCounts = new HashMap<>();
    ordered.forEach(candidate -> candidateCounts.merge(candidate.id(), 1, Integer::sum));

    return ordered.stream()
        .map(
            candidate ->
                activateCandidate(
                    candidate,
                    candidateCounts.get(candidate.id()) > 1,
                    environment,
                    enabled,
                    loader))
        .toList();
  }

  /** Returns active integration metadata sorted by stable identifier. */
  public synchronized List<IntegrationInfo> integrations() {
    return List.copyOf(integrations.values());
  }

  /** Returns the current tool and integration catalog as a detached capability snapshot. */
  public synchronized CapabilitiesSnapshot capabilities(String threadVersion) {
    List<String> toolIds = tools.descriptors().stream().map(tool -> tool.id().toString()).toList();
    List<IntegrationCapability> activeIntegrations =
        integrations.values().stream()
            .map(
                info ->
                    new IntegrationCapability(
                        info.id().toString(),
                        info.version(),
                        info.metadata().entrySet().stream()
                            .map(
                                entry ->
                                    new IntegrationMetadataEntry(entry.getKey(), entry.getValue()))
                            .toList()))
            .toList();
    return new CapabilitiesSnapshot(threadVersion, true, toolIds, activeIntegrations);
  }

  private IntegrationActivation activateCandidate(
      IntegrationCandidate candidate,
      boolean duplicateCandidate,
      IntegrationEnvironment environment,
      Predicate<IntegrationId> enabled,
      IntegrationLoader loader) {
    if (duplicateCandidate || integrations.containsKey(candidate.id())) {
      return activation(candidate, IntegrationActivationStatus.FAILED, null);
    }
    if (!enabled.test(candidate.id())) {
      return activation(candidate, IntegrationActivationStatus.DISABLED, null);
    }

    final Optional<String> installedVersion;
    try {
      installedVersion =
          Objects.requireNonNull(
              environment.loadedModVersion(candidate.targetModId()), "loaded mod version");
    } catch (RuntimeException | LinkageError exception) {
      logFailure(candidate, exception);
      return activation(candidate, IntegrationActivationStatus.FAILED, null);
    }
    if (installedVersion.isEmpty()) {
      return activation(candidate, IntegrationActivationStatus.UNAVAILABLE, null);
    }
    String version = installedVersion.orElseThrow();

    final boolean compatible;
    try {
      compatible =
          environment.versionCompatible(candidate.targetModId(), candidate.versionRequirement());
    } catch (RuntimeException | LinkageError exception) {
      logFailure(candidate, exception);
      return activation(candidate, IntegrationActivationStatus.FAILED, version);
    }
    if (!compatible) {
      return activation(candidate, IntegrationActivationStatus.INCOMPATIBLE, version);
    }

    try {
      ThreadIntegration integration = loader.load(candidate.implementationClassName());
      if (!candidate.id().equals(integration.id())) {
        throw new IllegalArgumentException(
            "loaded integration ID does not match its candidate declaration");
      }
      register(
          integration,
          Map.of(
              "thread.target_mod",
              candidate.targetModId(),
              "thread.target_mod_version",
              version,
              "thread.version_requirement",
              candidate.versionRequirement()));
      return activation(candidate, IntegrationActivationStatus.ACTIVE, version);
    } catch (IntegrationLoadException | RuntimeException | LinkageError exception) {
      logFailure(candidate, exception);
      return activation(candidate, IntegrationActivationStatus.FAILED, version);
    }
  }

  private void register(ThreadIntegration integration, Map<String, String> hostMetadata) {
    Objects.requireNonNull(integration, "integration");
    IntegrationId id = Objects.requireNonNull(integration.id(), "integration.id()");
    if (integrations.containsKey(id)) {
      throw new DuplicateRegistrationException("integration", id.toString());
    }

    IntegrationContext context = new IntegrationContext(id, tools, contexts, extensions);
    integration.register(context);
    context.prepareCommit();
    IntegrationInfo info =
        new IntegrationInfo(
            id, integration.version(), integration.description(), context.metadata(hostMetadata));
    context.commit();
    integrations.put(id, info);
  }

  private static IntegrationActivation activation(
      IntegrationCandidate candidate, IntegrationActivationStatus status, String targetModVersion) {
    return new IntegrationActivation(
        candidate.id(), status, candidate.targetModId(), targetModVersion);
  }

  private static void logFailure(IntegrationCandidate candidate, Throwable exception) {
    LOGGER.log(
        System.Logger.Level.WARNING,
        "Optional integration {0} failed safely ({1})",
        candidate.id(),
        exception.getClass().getName());
  }
}
