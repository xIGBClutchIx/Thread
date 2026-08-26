package me.clutchy.thread.core.integration.extension;

import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.tool.ToolResult;

/**
 * Guard-preserving recipe provider with deterministic optional-provider precedence.
 *
 * <p>The base provider is always read first so its session and safety checks remain authoritative.
 * The first optional provider, in stable integration-ID order, that returns at least one recipe
 * replaces the base result for that item. Empty, failed, or crashing optional providers are
 * skipped; when none handles the item, the successful base result is returned unchanged.
 */
public final class CompositeRecipeProvider implements RecipeProvider {
  private static final System.Logger LOGGER =
      System.getLogger(CompositeRecipeProvider.class.getName());

  private final RecipeProvider base;
  private final IntegrationExtensionRegistry extensions;

  public CompositeRecipeProvider(RecipeProvider base, IntegrationExtensionRegistry extensions) {
    this.base = Objects.requireNonNull(base, "base");
    this.extensions = Objects.requireNonNull(extensions, "extensions");
  }

  @Override
  public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
    ToolResult<List<RecipeInfo>> baseResult = base.recipesFor(itemId);
    if (!baseResult.successful()) {
      return baseResult;
    }
    List<RecipeInfo> baseRecipes = Objects.requireNonNull(baseResult.value(), "base recipes");
    for (IntegrationRecipeProvider provider :
        extensions.contributions(CoreIntegrationExtensionPoints.RECIPE_PROVIDER)) {
      try {
        ToolResult<List<RecipeInfo>> result =
            Objects.requireNonNull(provider.recipesFor(itemId), "integration recipe result");
        if (result.successful()) {
          List<RecipeInfo> integrationRecipes =
              Objects.requireNonNull(result.value(), "integration recipes");
          if (!integrationRecipes.isEmpty()) {
            return ToolResult.success(List.copyOf(integrationRecipes));
          }
        }
      } catch (RuntimeException | LinkageError exception) {
        // Optional recipe adapters must not make vanilla recipe queries unavailable at runtime.
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Optional recipe provider failed ({0})",
            exception.getClass().getName());
      }
    }
    return ToolResult.success(List.copyOf(baseRecipes));
  }

  @Override
  public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
    return base.searchItems(query, limit);
  }
}
