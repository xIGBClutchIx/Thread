package me.clutchy.thread.platform.minecraft.inspection;

import java.util.Objects;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.platform.minecraft.integration.MinecraftIntegrationExtensionPoints;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Applies isolated Minecraft target-block enrichers in deterministic integration order. */
public final class MinecraftBlockEnricherRegistry {
  private static final System.Logger LOGGER =
      System.getLogger(MinecraftBlockEnricherRegistry.class.getName());

  private final IntegrationExtensionRegistry extensions;

  public MinecraftBlockEnricherRegistry(IntegrationExtensionRegistry extensions) {
    this.extensions = Objects.requireNonNull(extensions, "extensions");
  }

  /** Returns the snapshot after every successful optional enrichment. */
  public BlockInfo enrich(
      ServerLevel level,
      BlockPos position,
      BlockState state,
      BlockEntity blockEntity,
      BlockInfo base) {
    BlockInfo current = Objects.requireNonNull(base, "base");
    for (MinecraftBlockEnricher enricher :
        extensions.contributions(MinecraftIntegrationExtensionPoints.BLOCK_ENRICHER)) {
      try {
        current =
            Objects.requireNonNull(
                enricher.enrich(level, position, state, blockEntity, current), "enriched block");
      } catch (RuntimeException | LinkageError exception) {
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Optional block enricher failed ({0})",
            exception.getClass().getName());
      }
    }
    return current;
  }
}
