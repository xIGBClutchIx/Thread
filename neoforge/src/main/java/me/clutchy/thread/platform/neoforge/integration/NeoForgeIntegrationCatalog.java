package me.clutchy.thread.platform.neoforge.integration;

import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationCandidateCatalog;

/** Collects metadata-only candidates from separately installed NeoForge integration packages. */
public final class NeoForgeIntegrationCatalog {
  private NeoForgeIntegrationCatalog() {}

  /** Returns external candidates discovered through the NeoForge game module layer. */
  public static List<IntegrationCandidate> candidates(ClassLoader modClassLoader) {
    Objects.requireNonNull(modClassLoader, "modClassLoader");
    return IntegrationCandidateCatalog.candidatesFromServices(modClassLoader);
  }
}
