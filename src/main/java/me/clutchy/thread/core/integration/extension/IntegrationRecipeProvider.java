package me.clutchy.thread.core.integration.extension;

import java.util.List;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.tool.ToolResult;

/**
 * Optional source of additional detached recipe definitions for an item.
 *
 * <p>The vanilla provider remains authoritative for session and safety failures. Integrations may
 * augment successful vanilla results but cannot replace the base provider or bypass session guards.
 */
@FunctionalInterface
public interface IntegrationRecipeProvider {
  /** Returns additional read-only recipes for the canonical result item ID. */
  ToolResult<List<RecipeInfo>> recipesFor(String itemId);
}
