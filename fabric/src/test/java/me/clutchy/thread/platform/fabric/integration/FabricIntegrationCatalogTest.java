package me.clutchy.thread.platform.fabric.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Supplier;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegrationCandidateProvider;
import org.junit.jupiter.api.Test;

class FabricIntegrationCatalogTest {
  @Test
  void collectsExternalCandidateMetadataAndIsolatesBrokenProviders() {
    IntegrationCandidate candidate =
        new IntegrationCandidate(
            IntegrationId.of("proof"), "proof-target", ">=1", "example.ProofIntegration");
    Supplier<ThreadIntegrationCandidateProvider> healthy = () -> () -> List.of(candidate);
    Supplier<ThreadIntegrationCandidateProvider> broken =
        () -> {
          throw new NoClassDefFoundError("optional bootstrap dependency");
        };

    assertEquals(
        List.of(candidate),
        FabricIntegrationCatalog.candidatesFromProviders(List.of(broken, healthy)));
  }
}
