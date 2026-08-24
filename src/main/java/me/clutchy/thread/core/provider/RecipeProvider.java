package me.clutchy.thread.core.provider;

import java.util.List;
import me.clutchy.thread.core.model.ItemInfo;
import me.clutchy.thread.core.model.RecipeInfo;
import me.clutchy.thread.core.tool.ToolResult;

/** Supplies loader-neutral item and recipe data from the current running game instance. */
public interface RecipeProvider {
  /** Returns every live recipe that produces the canonical item ID. */
  ToolResult<List<RecipeInfo>> recipesFor(String itemId);

  /** Returns at most {@code limit} matching items in deterministic order. */
  ToolResult<List<ItemInfo>> searchItems(String query, int limit);
}
