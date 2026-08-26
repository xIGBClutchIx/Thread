package me.clutchy.thread.platform.minecraft.world;

import java.util.Objects;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.platform.minecraft.integration.MinecraftIntegrationExtensionPoints;
import net.minecraft.world.entity.Entity;

/** Applies isolated Minecraft entity enrichers in deterministic integration order. */
public final class MinecraftEntityEnricherRegistry {
  private static final System.Logger LOGGER =
      System.getLogger(MinecraftEntityEnricherRegistry.class.getName());

  private final IntegrationExtensionRegistry extensions;

  public MinecraftEntityEnricherRegistry(IntegrationExtensionRegistry extensions) {
    this.extensions = Objects.requireNonNull(extensions, "extensions");
  }

  /** Returns the snapshot after every successful optional enrichment. */
  public EntityInfo enrich(Entity entity, EntityInfo base) {
    EntityInfo current = Objects.requireNonNull(base, "base");
    for (MinecraftEntityEnricher enricher :
        extensions.contributions(MinecraftIntegrationExtensionPoints.ENTITY_ENRICHER)) {
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
