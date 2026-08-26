package me.clutchy.thread.core.provider;

import java.util.List;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.tool.ToolResult;

/** Supplies loader-neutral item and recipe data from the current running game instance. */
public interface RecipeProvider {
  /** Returns every live recipe that produces the canonical item ID, or a structured failure. */
  ToolResult<List<RecipeInfo>> recipesFor(String itemId);

  /**
   * Returns at most {@code limit} matching items and accurate truncation metadata.
   *
   * <p>Implementations may enforce a lower configured safety ceiling.
   */
  ToolResult<ItemSearchResult> searchItems(String query, int limit);
}
