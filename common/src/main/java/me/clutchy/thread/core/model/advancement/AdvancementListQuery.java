package me.clutchy.thread.core.model.advancement;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Optional completion, text, and result bounds for listing known vanilla advancements. */
public record AdvancementListQuery(AdvancementFilter filter, String search, Integer limit) {
  public static final int DEFAULT_LIMIT = 64;
  public static final int MAX_LIMIT = 128;
  public static final int MAX_SEARCH_LENGTH = 128;

  public AdvancementListQuery {
    filter = filter == null ? AdvancementFilter.ALL : filter;
    search =
        search == null
            ? null
            : ModelValidation.boundedNonBlank(search.strip(), "search", MAX_SEARCH_LENGTH);
    limit = limit == null ? DEFAULT_LIMIT : limit;
    if (limit <= 0 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
    }
  }
}
