package dev.xigbclutch.thread.core.provider;

import dev.xigbclutch.thread.core.model.ItemInfo;
import dev.xigbclutch.thread.core.model.RecipeInfo;
import java.util.List;

/** Supplies loader-neutral item and recipe data from the current running game instance. */
public interface RecipeProvider {
  /** Returns every live recipe that produces the canonical item ID. */
  List<RecipeInfo> recipesFor(String itemId);

  /** Returns at most {@code limit} matching items in deterministic order. */
  List<ItemInfo> searchItems(String query, int limit);
}
