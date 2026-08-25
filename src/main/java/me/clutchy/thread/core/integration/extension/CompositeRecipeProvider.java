package me.clutchy.thread.core.integration.extension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.tool.ToolResult;

/** Guard-preserving recipe provider that adds isolated optional integration results. */
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
    LinkedHashSet<RecipeInfo> combined =
        new LinkedHashSet<>(Objects.requireNonNull(baseResult.value(), "base recipes"));
    for (IntegrationRecipeProvider provider :
        extensions.contributions(CoreIntegrationExtensionPoints.RECIPE_PROVIDER)) {
      try {
        ToolResult<List<RecipeInfo>> result =
            Objects.requireNonNull(provider.recipesFor(itemId), "integration recipe result");
        if (result.successful()) {
          combined.addAll(Objects.requireNonNull(result.value(), "integration recipes"));
        }
      } catch (RuntimeException | LinkageError exception) {
        // Optional recipe adapters must not make vanilla recipe queries unavailable at runtime.
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Optional recipe provider failed ({0})",
            exception.getClass().getName());
      }
    }
    return ToolResult.success(List.copyOf(combined));
  }

  @Override
  public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
    return base.searchItems(query, limit);
  }
}
