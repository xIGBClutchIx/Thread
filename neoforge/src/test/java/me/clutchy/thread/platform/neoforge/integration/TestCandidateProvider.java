package me.clutchy.thread.platform.neoforge.integration;

import java.util.List;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegrationCandidateProvider;

/** Test service proving that NeoForge integrations can advertise metadata without class loading. */
public final class TestCandidateProvider implements ThreadIntegrationCandidateProvider {
  @Override
  public List<IntegrationCandidate> candidates() {
    return List.of(
        new IntegrationCandidate(
            IntegrationId.of("neoforge-service-proof"),
            "thread-neoforge-catalog-test",
            "[1.0,)",
            "example.NeoForgeServiceProofIntegration"));
  }
}
