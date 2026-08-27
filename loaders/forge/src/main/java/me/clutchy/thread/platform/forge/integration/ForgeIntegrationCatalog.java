package me.clutchy.thread.platform.forge.integration;

import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationCandidateCatalog;

/** Collects metadata-only candidates from separately installed Forge integration packages. */
public final class ForgeIntegrationCatalog {
  private ForgeIntegrationCatalog() {}

  /** Returns external candidates discovered through the Forge game classloader. */
  public static List<IntegrationCandidate> candidates(ClassLoader modClassLoader) {
    Objects.requireNonNull(modClassLoader, "modClassLoader");
    return IntegrationCandidateCatalog.candidatesFromServices(modClassLoader);
  }
}
