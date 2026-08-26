package me.clutchy.thread.platform.fabric.integration;

import java.util.List;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegrationCandidateProvider;

/** Test-mod bootstrap proving that an external Fabric mod can advertise Thread integrations. */
public final class ExternalCandidateProvider implements ThreadIntegrationCandidateProvider {
  @Override
  public List<IntegrationCandidate> candidates() {
    return List.of(
        new IntegrationCandidate(
            IntegrationId.of("gametest-bridge"),
            "thread-gametest",
            ">=1.0.0",
            "me.clutchy.thread.platform.fabric.integration.ExternalProofIntegration"));
  }
}
