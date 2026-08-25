package me.clutchy.thread.platform.fabric.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Supplier;
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.ThreadIntegration;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.platform.fabric.game.FabricProviderLimits;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import org.junit.jupiter.api.Test;

class FabricIntegrationCatalogTest {
  @Test
  void declaresTheSupportedJeiCandidateWithoutAClassLiteral() {
    List<IntegrationCandidate> candidates = FabricIntegrationCatalog.candidates();

    assertEquals(1, candidates.size());
    IntegrationCandidate candidate = candidates.getFirst();
    assertEquals("jei", candidate.id().value());
    assertEquals("jei", candidate.targetModId());
    assertEquals(">=30.26.0.182 <31", candidate.versionRequirement());
    assertEquals(
        "me.clutchy.thread.platform.fabric.integration.jei.JeiIntegration",
        candidate.implementationClassName());
  }

  @Test
  void fabricLoaderInjectsOnlyPlatformServicesAfterDiscovery() throws Exception {
    GameThreadExecutor direct =
        new GameThreadExecutor() {
          @Override
          public <T> T call(Supplier<T> operation) {
            return operation.get();
          }
        };
    FabricIntegrationServices services =
        new FabricIntegrationServices(
            direct, FabricProviderLimits.defaults(), new FabricDtoMapper());
    FabricIntegrationLoader loader =
        new FabricIntegrationLoader(services, getClass().getClassLoader());

    ThreadIntegration integration =
        loader.load("me.clutchy.thread.platform.fabric.integration.jei.JeiIntegration");

    assertEquals("jei", integration.id().value());
  }
}
