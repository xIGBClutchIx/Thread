package me.clutchy.thread.platform.neoforge.integration;

import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationCandidateCatalog;
import me.clutchy.thread.core.integration.ThreadIntegrationCandidateProvider;

/** Collects metadata-only candidates from separately installed NeoForge integration packages. */
public final class NeoForgeIntegrationCatalog {
  private static final System.Logger LOGGER =
      System.getLogger(NeoForgeIntegrationCatalog.class.getName());

  private NeoForgeIntegrationCatalog() {}

  /** Returns external candidates discovered through the NeoForge game module layer. */
  public static List<IntegrationCandidate> candidates(ClassLoader modClassLoader) {
    Objects.requireNonNull(modClassLoader, "modClassLoader");
    try {
      List<Supplier<ThreadIntegrationCandidateProvider>> providers =
          ServiceLoader.load(ThreadIntegrationCandidateProvider.class, modClassLoader).stream()
              .<Supplier<ThreadIntegrationCandidateProvider>>map(provider -> provider::get)
              .toList();
      return IntegrationCandidateCatalog.candidatesFromProviders(providers);
    } catch (ServiceConfigurationError error) {
      // Malformed external service metadata must not prevent Thread's native integration from
      // starting. Provider construction failures remain isolated individually by the catalog.
      LOGGER.log(
          System.Logger.Level.WARNING,
          "Skipped malformed Thread integration service metadata ({0})",
          error.getClass().getName());
      return List.of();
    }
  }
}
