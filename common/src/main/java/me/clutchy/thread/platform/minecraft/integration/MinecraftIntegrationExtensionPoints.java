package me.clutchy.thread.platform.minecraft.integration;

import me.clutchy.thread.core.integration.extension.IntegrationExtensionPoint;
import me.clutchy.thread.platform.minecraft.inspection.MinecraftBlockEnricher;
import me.clutchy.thread.platform.minecraft.inspection.MinecraftBlockEntityInspector;
import me.clutchy.thread.platform.minecraft.world.MinecraftEntityEnricher;

/** Shared extension points whose contracts intentionally use Minecraft implementation types. */
public final class MinecraftIntegrationExtensionPoints {
  /** Safe structured inspection for recognized block entities. */
  public static final IntegrationExtensionPoint<MinecraftBlockEntityInspector>
      BLOCK_ENTITY_INSPECTOR =
          new IntegrationExtensionPoint<>(
              "minecraft.block_entity_inspector", MinecraftBlockEntityInspector.class);

  /** Read-only enrichment of an already detached target-block snapshot. */
  public static final IntegrationExtensionPoint<MinecraftBlockEnricher> BLOCK_ENRICHER =
      new IntegrationExtensionPoint<>("minecraft.block_enricher", MinecraftBlockEnricher.class);

  /** Read-only enrichment of an already detached nearby-entity snapshot. */
  public static final IntegrationExtensionPoint<MinecraftEntityEnricher> ENTITY_ENRICHER =
      new IntegrationExtensionPoint<>("minecraft.entity_enricher", MinecraftEntityEnricher.class);

  private MinecraftIntegrationExtensionPoints() {}
}
