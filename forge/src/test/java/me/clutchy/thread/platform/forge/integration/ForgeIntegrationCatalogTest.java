package me.clutchy.thread.platform.forge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationId;
import org.junit.jupiter.api.Test;

class ForgeIntegrationCatalogTest {
  @Test
  void discoversCandidateProvidersThroughJavaServices() {
    IntegrationCandidate expected =
        new IntegrationCandidate(
            IntegrationId.of("forge-service-proof"),
            "thread-forge-catalog-test",
            "[1.0,)",
            "example.ForgeServiceProofIntegration");

    assertEquals(
        List.of(expected), ForgeIntegrationCatalog.candidates(getClass().getClassLoader()));
  }
}
