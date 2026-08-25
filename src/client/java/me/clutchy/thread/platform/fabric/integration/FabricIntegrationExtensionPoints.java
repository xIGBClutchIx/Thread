package me.clutchy.thread.platform.fabric.integration;

import me.clutchy.thread.core.integration.extension.IntegrationExtensionPoint;
import me.clutchy.thread.platform.fabric.inspection.FabricBlockEnricher;
import me.clutchy.thread.platform.fabric.inspection.FabricBlockEntityInspector;
import me.clutchy.thread.platform.fabric.world.FabricEntityEnricher;

/** Fabric-owned extension points whose contracts may use Minecraft implementation types. */
public final class FabricIntegrationExtensionPoints {
  /** Safe structured inspection for recognized block entities. */
  public static final IntegrationExtensionPoint<FabricBlockEntityInspector> BLOCK_ENTITY_INSPECTOR =
      new IntegrationExtensionPoint<>(
          "fabric.block_entity_inspector", FabricBlockEntityInspector.class);

  /** Read-only enrichment of an already detached target-block snapshot. */
  public static final IntegrationExtensionPoint<FabricBlockEnricher> BLOCK_ENRICHER =
      new IntegrationExtensionPoint<>("fabric.block_enricher", FabricBlockEnricher.class);

  /** Read-only enrichment of an already detached nearby-entity snapshot. */
  public static final IntegrationExtensionPoint<FabricEntityEnricher> ENTITY_ENRICHER =
      new IntegrationExtensionPoint<>("fabric.entity_enricher", FabricEntityEnricher.class);

  private FabricIntegrationExtensionPoints() {}
}
