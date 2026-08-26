package me.clutchy.thread.core.model.item;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Bounded user-facing query over the running game's item registry. */
public record ItemSearchQuery(String query, int limit) {
  public ItemSearchQuery {
    query = ModelValidation.nonBlank(query, "query").strip();
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
  }
}
