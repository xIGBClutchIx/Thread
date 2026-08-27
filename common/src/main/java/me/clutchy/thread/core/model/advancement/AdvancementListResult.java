package me.clutchy.thread.core.model.advancement;

import java.util.Comparator;
import java.util.List;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Deterministic filtered view over a bounded known-advancement snapshot. */
public record AdvancementListResult(
    AdvancementFilter filter,
    String search,
    int limit,
    int knownCount,
    int scannedCount,
    int matchedCount,
    boolean sourceTruncated,
    boolean truncated,
    List<AdvancementSummary> advancements) {
  public AdvancementListResult {
    if (filter == null) {
      throw new IllegalArgumentException("filter must not be null");
    }
    search =
        search == null
            ? null
            : ModelValidation.boundedNonBlank(
                search.strip(), "search", AdvancementListQuery.MAX_SEARCH_LENGTH);
    if (limit <= 0 || limit > AdvancementListQuery.MAX_LIMIT) {
      throw new IllegalArgumentException("limit is outside the supported range");
    }
    if (knownCount < 0 || scannedCount < 0 || matchedCount < 0 || knownCount < scannedCount) {
      throw new IllegalArgumentException("advancement result counts are inconsistent");
    }
    advancements =
        ModelValidation.immutableList(advancements, "advancements").stream()
            .sorted(Comparator.comparing(AdvancementSummary::advancementId))
            .toList();
    if (advancements.size() > limit || matchedCount < advancements.size()) {
      throw new IllegalArgumentException("returned advancements exceed the result counts");
    }
    if (sourceTruncated != (knownCount > scannedCount)) {
      throw new IllegalArgumentException("sourceTruncated must describe the provider snapshot");
    }
    if (truncated != (sourceTruncated || matchedCount > advancements.size())) {
      throw new IllegalArgumentException("truncated must describe omitted results");
    }
  }
}
