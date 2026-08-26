package me.clutchy.thread.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class IntegrationCandidateCatalogTest {
  @Test
  void isolatesBrokenProvidersAndKeepsLaterCandidates() {
    IntegrationCandidate expected =
        new IntegrationCandidate(
            IntegrationId.of("working"), "example", ">=1", "example.WorkingIntegration");
    List<Supplier<ThreadIntegrationCandidateProvider>> providers =
        List.of(
            () -> {
              throw new LinkageError("broken optional bootstrap");
            },
            () -> () -> List.of(expected));

    assertEquals(List.of(expected), IntegrationCandidateCatalog.candidatesFromProviders(providers));
  }
}
