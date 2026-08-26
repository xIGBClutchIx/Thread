package me.clutchy.thread.core.integration.extension;

import java.util.List;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.tool.ToolResult;

/**
 * Optional preferred source of detached recipe definitions for an item.
 *
 * <p>The vanilla provider remains authoritative for session and safety failures. Integrations may
 * replace a successful base result only by returning a non-empty list. An empty result or
 * controlled failure declines the item and preserves the base provider's live recipes.
 */
@FunctionalInterface
public interface IntegrationRecipeProvider {
  /**
   * Returns preferred read-only recipes, or an empty result when this source does not handle it.
   */
  ToolResult<List<RecipeInfo>> recipesFor(String itemId);
}
