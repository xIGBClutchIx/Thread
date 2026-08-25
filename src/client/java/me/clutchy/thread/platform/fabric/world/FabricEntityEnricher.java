package me.clutchy.thread.platform.fabric.world;

import me.clutchy.thread.core.model.world.EntityInfo;
import net.minecraft.world.entity.Entity;

/**
 * Fabric-side extension point for enriching one already-bounded nearby-entity snapshot.
 *
 * <p>Implementations may inspect the live entity on the owning game thread, but must return only a
 * detached {@link EntityInfo} value.
 */
@FunctionalInterface
public interface FabricEntityEnricher {
  /** Returns a detached replacement snapshot for the supplied live entity. */
  EntityInfo enrich(Entity entity, EntityInfo current);
}
