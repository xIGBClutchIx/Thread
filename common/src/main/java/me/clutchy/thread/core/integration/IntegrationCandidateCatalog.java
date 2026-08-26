package me.clutchy.thread.core.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Isolated collection of metadata-only candidates supplied by a loader adapter. */
public final class IntegrationCandidateCatalog {
  private static final System.Logger LOGGER =
      System.getLogger(IntegrationCandidateCatalog.class.getName());

  private IntegrationCandidateCatalog() {}

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
