package me.clutchy.thread.platform.minecraft.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.platform.minecraft.integration.MinecraftIntegrationExtensionPoints;
import org.junit.jupiter.api.Test;

class MinecraftBlockEnricherRegistryTest {
  @Test
  void appliesContributionsInIdOrderAndIsolatesFailures() {
    IntegrationExtensionRegistry extensions = new IntegrationExtensionRegistry();
    extensions.register(
        IntegrationId.of("zulu"),
        MinecraftIntegrationExtensionPoints.BLOCK_ENRICHER,
        (level, position, state, blockEntity, current) -> relabel(current, "zulu"));
    extensions.register(
        IntegrationId.of("broken"),
        MinecraftIntegrationExtensionPoints.BLOCK_ENRICHER,
        (level, position, state, blockEntity, current) -> {
          throw new NoClassDefFoundError("optional block API");
        });
    extensions.register(
        IntegrationId.of("alpha"),
        MinecraftIntegrationExtensionPoints.BLOCK_ENRICHER,
        (level, position, state, blockEntity, current) -> relabel(current, "alpha"));
    MinecraftBlockEnricherRegistry registry = new MinecraftBlockEnricherRegistry(extensions);

    BlockInfo result = registry.enrich(null, null, null, null, block("base"));

    assertEquals("base-alpha-zulu", result.displayName());
  }

  private static BlockInfo block(String displayName) {
    return new BlockInfo(
        "minecraft:stone", displayName, new BlockPosition(0, 64, 0), Map.of(), 2, false, null);
  }

  private static BlockInfo relabel(BlockInfo current, String suffix) {
    return new BlockInfo(
        current.blockId(),
        current.displayName() + "-" + suffix,
        current.position(),
        current.properties(),
        current.distance(),
        current.blockEntityPresent(),
        current.blockEntity());
  }
}
