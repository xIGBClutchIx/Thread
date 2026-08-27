package me.clutchy.thread.platform.neoforge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationId;
import org.junit.jupiter.api.Test;

class NeoForgeIntegrationCatalogTest {
  @Test
  void discoversCandidateProvidersThroughJavaServices() {
    IntegrationCandidate expected =
        new IntegrationCandidate(
            IntegrationId.of("neoforge-service-proof"),
            "thread-neoforge-catalog-test",
            "[1.0,)",
            "example.NeoForgeServiceProofIntegration");

    assertEquals(
        List.of(expected), NeoForgeIntegrationCatalog.candidates(getClass().getClassLoader()));
  }
}
