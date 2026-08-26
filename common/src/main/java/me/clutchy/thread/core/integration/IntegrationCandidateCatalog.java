package me.clutchy.thread.core.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/** Isolated collection of metadata-only candidates supplied by a loader adapter. */
public final class IntegrationCandidateCatalog {
  private static final System.Logger LOGGER =
      System.getLogger(IntegrationCandidateCatalog.class.getName());

  private IntegrationCandidateCatalog() {}

  /**
   * Discovers metadata providers through standard Java services using a loader-owned classloader.
   *
   * <p>Forge-family loaders use the same service mechanism, while retaining ownership of the
   * classloader and service metadata in their adapter artifacts.
   *
   * @param classLoader active loader's mod classloader
   * @return isolated candidate metadata in service discovery order
   */
  public static List<IntegrationCandidate> candidatesFromServices(ClassLoader classLoader) {
    Objects.requireNonNull(classLoader, "classLoader");
    try {
      List<Supplier<ThreadIntegrationCandidateProvider>> providers =
          ServiceLoader.load(ThreadIntegrationCandidateProvider.class, classLoader).stream()
              .<Supplier<ThreadIntegrationCandidateProvider>>map(provider -> provider::get)
              .toList();
      return candidatesFromProviders(providers);
    } catch (ServiceConfigurationError error) {
      // Invalid external service metadata must not prevent Thread's native integration from
      // starting. Individual provider construction remains isolated by candidatesFromProviders.
      LOGGER.log(
          System.Logger.Level.WARNING,
          "Skipped malformed Thread integration service metadata ({0})",
          error.getClass().getName());
      return List.of();
    }
  }

  /**
   * Resolves candidate providers independently so one broken external bootstrap cannot stop Thread.
   *
   * @param providers lazy provider bootstraps discovered by the active loader
   * @return detached candidate metadata in discovery order
   */
  public static List<IntegrationCandidate> candidatesFromProviders(
      List<? extends Supplier<ThreadIntegrationCandidateProvider>> providers) {
    Objects.requireNonNull(providers, "providers");
    List<IntegrationCandidate> candidates = new ArrayList<>();
    for (Supplier<ThreadIntegrationCandidateProvider> providerSupplier : providers) {
      try {
        ThreadIntegrationCandidateProvider provider =
            Objects.requireNonNull(providerSupplier, "providerSupplier").get();
        List<IntegrationCandidate> contributed =
            List.copyOf(Objects.requireNonNull(provider, "provider").candidates());
        candidates.addAll(contributed);
      } catch (RuntimeException | LinkageError exception) {
        // Provider bootstraps are external code. Isolation here preserves native startup and lets
        // later packages contribute even when one bootstrap cannot link or construct.
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Skipped one Thread integration candidate provider ({0})",
            exception.getClass().getName());
      }
    }
    return List.copyOf(candidates);
  }
}
