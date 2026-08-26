package me.clutchy.thread.core.model.item.find;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Deterministic bounded result for unified live item search. */
public record FindItemResult(
    String query,
    double radius,
    int containerLimit,
    int itemLimit,
    boolean containersTruncated,
    boolean itemsTruncated,
    List<FoundItem> matches) {
  public FindItemResult {
    query = ModelValidation.boundedNonBlank(query, "query", 128).strip();
    ModelValidation.finite(radius, "radius");
    if (radius <= 0) {
      throw new IllegalArgumentException("radius must be positive");
    }
    if (containerLimit <= 0 || itemLimit <= 0) {
      throw new IllegalArgumentException("limits must be positive");
    }
    matches =
        ModelValidation.immutableList(matches, "matches").stream()
            .sorted(Comparator.comparing(found -> found.item().itemId()))
            .toList();
    if (matches.size() > itemLimit) {
      throw new IllegalArgumentException("match count must not exceed itemLimit");
    }
    if (itemsTruncated && matches.size() != itemLimit) {
      throw new IllegalArgumentException("truncated item matches must fill itemLimit");
    }
    Set<String> itemIds = new HashSet<>();
    for (FoundItem match : matches) {
      if (!itemIds.add(match.item().itemId())) {
        throw new IllegalArgumentException(
            "matches contains duplicate itemId " + match.item().itemId());
      }
    }
  }
}
