package me.clutchy.thread.platform.fabric.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.ThreadIntegrationCandidateProvider;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Collects metadata-only candidates from separately installed Fabric integration packages.
 *
 * <p>The base artifact intentionally declares no third-party candidates. External packages expose a
 * {@link ThreadIntegrationCandidateProvider} through {@value #ENTRYPOINT_KEY}. That bootstrap must
 * remain free of target-mod APIs; candidates still use implementation class-name strings so normal
 * enabled, presence, and compatibility checks run before the actual adapter is loaded.
 */
public final class FabricIntegrationCatalog {
  /** Fabric entrypoint key used by separately distributed Thread Integrations packages. */
  public static final String ENTRYPOINT_KEY = "thread:integrations";

  private static final System.Logger LOGGER =
      System.getLogger(FabricIntegrationCatalog.class.getName());
  private static final List<IntegrationCandidate> BUNDLED_CANDIDATES = List.of();

  private FabricIntegrationCatalog() {}

  /**
   * Returns bundled and external candidates; registry discovery applies stable ID ordering.
   *
   * @param loader active Fabric Loader instance
   * @return detached candidate metadata
   */
  public static List<IntegrationCandidate> candidates(FabricLoader loader) {
    Objects.requireNonNull(loader, "loader");
    List<Supplier<ThreadIntegrationCandidateProvider>> providers =
        loader
            .getEntrypointContainers(ENTRYPOINT_KEY, ThreadIntegrationCandidateProvider.class)
            .stream()
            .<Supplier<ThreadIntegrationCandidateProvider>>map(
                container -> container::getEntrypoint)
            .toList();
    return candidatesFromProviders(providers);
  }

  static List<IntegrationCandidate> bundledCandidates() {
    return BUNDLED_CANDIDATES;
  }

  static List<IntegrationCandidate> candidatesFromProviders(
      List<? extends Supplier<ThreadIntegrationCandidateProvider>> providers) {
    Objects.requireNonNull(providers, "providers");
    List<IntegrationCandidate> candidates = new ArrayList<>(BUNDLED_CANDIDATES);
    for (Supplier<ThreadIntegrationCandidateProvider> providerSupplier : providers) {
      try {
        ThreadIntegrationCandidateProvider provider =
            Objects.requireNonNull(providerSupplier, "providerSupplier").get();
        List<IntegrationCandidate> contributed =
            List.copyOf(Objects.requireNonNull(provider, "provider").candidates());
        candidates.addAll(contributed);
      } catch (RuntimeException | LinkageError exception) {
        // A broken separately installed bootstrap must not prevent native Thread startup or hide
        // candidates from other integration packages.
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Skipped one Thread integration candidate provider ({0})",
            exception.getClass().getName());
      }
    }
    return List.copyOf(candidates);
  }
}
