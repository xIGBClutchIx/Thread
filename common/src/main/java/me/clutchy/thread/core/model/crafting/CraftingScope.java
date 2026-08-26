package me.clutchy.thread.core.model.crafting;

/** Item sources eligible for one read-only crafting assessment or plan. */
public enum CraftingScope {
  /** Uses only the player's 36-slot main inventory and preserves Thread's original behavior. */
  PLAYER_ONLY,

  /** Adds eligible nearby loaded containers to the player's main inventory. */
  PLAYER_AND_NEARBY
}
