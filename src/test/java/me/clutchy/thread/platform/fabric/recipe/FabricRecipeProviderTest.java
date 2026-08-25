package me.clutchy.thread.platform.fabric.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.clutchy.thread.core.model.item.ItemSearchResult;
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
    ItemSearchResult result = FabricRecipeProvider.searchRegistry("diamond pick", 10);

    assertTrue(
        result.items().stream()
            .anyMatch(item -> item.itemId().equals("minecraft:diamond_pickaxe")));
  }

  @Test
  void registrySearchIsDeterministicAndResultBounded() {
    ItemSearchResult first = FabricRecipeProvider.searchRegistry("minecraft", 2);
    ItemSearchResult second = FabricRecipeProvider.searchRegistry("minecraft", 2);

    assertEquals(2, first.items().size());
    assertTrue(first.truncated());
    assertEquals(first, second);
  }
}
