package me.clutchy.thread.platform.forge.integration;

import java.util.List;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegrationCandidateProvider;

/** Test service proving that Forge integrations can advertise metadata without class loading. */
public final class TestCandidateProvider implements ThreadIntegrationCandidateProvider {
  @Override
  public List<IntegrationCandidate> candidates() {
    return List.of(
        new IntegrationCandidate(
            IntegrationId.of("forge-service-proof"),
            "thread-forge-catalog-test",
            "[1.0,)",
            "example.ForgeServiceProofIntegration"));
  }
}
