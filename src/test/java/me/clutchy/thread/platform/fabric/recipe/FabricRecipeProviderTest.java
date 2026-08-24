package me.clutchy.thread.platform.fabric.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.clutchy.thread.core.model.ItemInfo;
import me.clutchy.thread.platform.fabric.testing.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FabricRecipeProviderTest {
  @BeforeAll
  static void bootstrapMinecraftRegistries() {
    MinecraftTestBootstrap.initialize();
  }

  @Test
  void searchesTheRealItemRegistryByFriendlyTerms() {
    List<ItemInfo> matches = FabricRecipeProvider.searchRegistry("diamond pick", 10);

    assertTrue(
        matches.stream().anyMatch(item -> item.itemId().equals("minecraft:diamond_pickaxe")));
  }

  @Test
  void registrySearchIsDeterministicAndResultBounded() {
    List<ItemInfo> first = FabricRecipeProvider.searchRegistry("minecraft", 2);
    List<ItemInfo> second = FabricRecipeProvider.searchRegistry("minecraft", 2);

    assertEquals(2, first.size());
    assertEquals(first, second);
  }
}
