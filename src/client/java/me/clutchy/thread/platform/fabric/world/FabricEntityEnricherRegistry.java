package me.clutchy.thread.platform.fabric.world;

import java.util.Objects;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationExtensionPoints;
import net.minecraft.world.entity.Entity;

/** Applies isolated Fabric entity enrichers in deterministic integration order. */
public final class FabricEntityEnricherRegistry {
  private static final System.Logger LOGGER =
      System.getLogger(FabricEntityEnricherRegistry.class.getName());

  private final IntegrationExtensionRegistry extensions;

  public FabricEntityEnricherRegistry(IntegrationExtensionRegistry extensions) {
    this.extensions = Objects.requireNonNull(extensions, "extensions");
  }

  /** Returns the snapshot after every successful optional enrichment. */
  public EntityInfo enrich(Entity entity, EntityInfo base) {
    EntityInfo current = Objects.requireNonNull(base, "base");
    for (FabricEntityEnricher enricher :
        extensions.contributions(FabricIntegrationExtensionPoints.ENTITY_ENRICHER)) {
      try {
        current = Objects.requireNonNull(enricher.enrich(entity, current), "enriched entity");
      } catch (RuntimeException | LinkageError exception) {
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Optional entity enricher failed ({0})",
            exception.getClass().getName());
      }
    }
    return current;
  }
}
