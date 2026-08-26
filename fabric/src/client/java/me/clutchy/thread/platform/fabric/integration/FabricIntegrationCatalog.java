package me.clutchy.thread.platform.fabric.integration;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationCandidateCatalog;
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

  private FabricIntegrationCatalog() {}

  /**
   * Returns external candidates; registry discovery applies stable ID ordering.
   *
   * @param loader active Fabric Loader instance
   * @return detached candidate metadata
   */
  public static List<IntegrationCandidate> candidates(FabricLoader loader) {
    Objects.requireNonNull(loader, "loader");
    return IntegrationCandidateCatalog.candidatesFromProviders(
        loader
            .getEntrypointContainers(ENTRYPOINT_KEY, ThreadIntegrationCandidateProvider.class)
            .stream()
            .<Supplier<ThreadIntegrationCandidateProvider>>map(
                container -> container::getEntrypoint)
            .toList());
  }
}
