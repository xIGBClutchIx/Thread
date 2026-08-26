package me.clutchy.thread.core.integration.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.model.item.ItemInfo;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class CompositeRecipeProviderTest {
  @Test
  void firstSuccessfulNonEmptyIntegrationWinsInStableIdOrder() {
    IntegrationExtensionRegistry extensions = new IntegrationExtensionRegistry();
    extensions.register(
        IntegrationId.of("zeta"),
        CoreIntegrationExtensionPoints.RECIPE_PROVIDER,
        itemId -> ToolResult.success(List.of(recipe("zeta:recipe", itemId))));
    extensions.register(
        IntegrationId.of("alpha"),
        CoreIntegrationExtensionPoints.RECIPE_PROVIDER,
        itemId -> ToolResult.success(List.of(recipe("alpha:recipe", itemId))));
    CompositeRecipeProvider provider =
        new CompositeRecipeProvider(new FakeBaseProvider(), extensions);

    ToolResult<List<RecipeInfo>> result = provider.recipesFor("minecraft:stick");

    assertTrue(result.successful());
    assertEquals(
        List.of("alpha:recipe"), result.value().stream().map(RecipeInfo::recipeId).toList());
  }

  @Test
  void fallsBackToBaseWhenOptionalProvidersDeclineOrFail() {
    IntegrationExtensionRegistry extensions = new IntegrationExtensionRegistry();
    extensions.register(
        IntegrationId.of("empty"),
        CoreIntegrationExtensionPoints.RECIPE_PROVIDER,
        itemId -> ToolResult.success(List.of()));
    extensions.register(
        IntegrationId.of("failed"),
        CoreIntegrationExtensionPoints.RECIPE_PROVIDER,
        itemId ->
            ToolResult.failure(
                ToolError.of(ToolErrorCode.NOT_AVAILABLE, "Optional recipes unavailable.", true)));
    extensions.register(
        IntegrationId.of("throwing"),
        CoreIntegrationExtensionPoints.RECIPE_PROVIDER,
        itemId -> {
          throw new NoClassDefFoundError("optional recipe API");
        });
    CompositeRecipeProvider provider =
        new CompositeRecipeProvider(new FakeBaseProvider(), extensions);

    ToolResult<List<RecipeInfo>> result = provider.recipesFor("minecraft:stick");

    assertTrue(result.successful());
    assertEquals(
        List.of("minecraft:base"), result.value().stream().map(RecipeInfo::recipeId).toList());
  }

  @Test
  void baseSessionFailureWinsWithoutCallingOptionalProviders() {
    IntegrationExtensionRegistry extensions = new IntegrationExtensionRegistry();
    AtomicBoolean optionalCalled = new AtomicBoolean();
    extensions.register(
        IntegrationId.of("optional"),
        CoreIntegrationExtensionPoints.RECIPE_PROVIDER,
        itemId -> {
          optionalCalled.set(true);
          return ToolResult.success(List.of());
        });
    RecipeProvider unavailableBase =
        new RecipeProvider() {
          @Override
          public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
            return ToolResult.failure(
                ToolError.of(ToolErrorCode.UNSUPPORTED, "Multiplayer is unsupported.", false));
          }

          @Override
          public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
            throw new AssertionError("not expected");
          }
        };
    CompositeRecipeProvider provider = new CompositeRecipeProvider(unavailableBase, extensions);

    ToolResult<List<RecipeInfo>> result = provider.recipesFor("minecraft:stick");

    assertFalse(result.successful());
    assertEquals(ToolErrorCode.UNSUPPORTED, result.error().code());
    assertFalse(optionalCalled.get());
  }

  @Test
  void itemSearchRemainsTheVanillaProviderBehavior() {
    CompositeRecipeProvider provider =
        new CompositeRecipeProvider(new FakeBaseProvider(), new IntegrationExtensionRegistry());

    ToolResult<ItemSearchResult> result = provider.searchItems("stick", 4);

    assertTrue(result.successful());
    assertEquals("minecraft:stick", result.value().items().getFirst().itemId());
  }

  private static RecipeInfo recipe(String recipeId, String itemId) {
    return new RecipeInfo(
        recipeId,
        "minecraft:crafting_shapeless",
        new ItemStackInfo(itemId, "Stick", null, 1, 64, null, List.of(), null),
        List.of());
  }

  private static final class FakeBaseProvider implements RecipeProvider {
    @Override
    public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
      return ToolResult.success(List.of(recipe("minecraft:base", itemId)));
    }

    @Override
    public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
      return ToolResult.success(
          new ItemSearchResult(
              query, limit, false, List.of(new ItemInfo("minecraft:stick", "Stick"))));
    }
  }
}
