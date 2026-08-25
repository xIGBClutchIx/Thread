package me.clutchy.thread.platform.fabric.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.Position;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationExtensionPoints;
import org.junit.jupiter.api.Test;

class FabricEntityEnricherRegistryTest {
  @Test
  void appliesContributionsInIdOrderAndIsolatesFailures() {
    IntegrationExtensionRegistry extensions = new IntegrationExtensionRegistry();
    extensions.register(
        IntegrationId.of("zulu"),
        FabricIntegrationExtensionPoints.ENTITY_ENRICHER,
        (entity, current) -> rename(current, "zulu"));
    extensions.register(
        IntegrationId.of("broken"),
        FabricIntegrationExtensionPoints.ENTITY_ENRICHER,
        (entity, current) -> {
          throw new NoClassDefFoundError("optional entity API");
        });
    extensions.register(
        IntegrationId.of("alpha"),
        FabricIntegrationExtensionPoints.ENTITY_ENRICHER,
        (entity, current) -> rename(current, "alpha"));
    FabricEntityEnricherRegistry registry = new FabricEntityEnricherRegistry(extensions);

    EntityInfo result = registry.enrich(null, entity("base"));

    assertEquals("base-alpha-zulu", result.customName());
  }

  private static EntityInfo entity(String customName) {
    return new EntityInfo(
        "minecraft:pig", "Pig", customName, 4, new Position(0, 64, 0), true, 10.0, 10.0, null);
  }

  private static EntityInfo rename(EntityInfo current, String suffix) {
    return new EntityInfo(
        current.entityType(),
        current.displayName(),
        current.customName() + "-" + suffix,
        current.distance(),
        current.position(),
        current.living(),
        current.health(),
        current.maxHealth(),
        current.classification());
  }
}
