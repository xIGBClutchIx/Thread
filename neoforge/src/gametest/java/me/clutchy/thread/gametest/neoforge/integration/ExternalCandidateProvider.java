package me.clutchy.thread.gametest.neoforge.integration;

import java.util.List;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegrationCandidateProvider;

/** Test service proving that another NeoForge mod can advertise a Thread integration. */
public final class ExternalCandidateProvider implements ThreadIntegrationCandidateProvider {
  @Override
  public List<IntegrationCandidate> candidates() {
    return List.of(
        new IntegrationCandidate(
            IntegrationId.of("gametest-bridge"),
            "thread_neoforge_gametest",
            "[1.0,)",
            "me.clutchy.thread.gametest.neoforge.integration.ExternalProofIntegration"));
  }
}
