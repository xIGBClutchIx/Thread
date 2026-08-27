package me.clutchy.thread.core.model.advancement;

import java.util.Comparator;
import java.util.List;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Bounded provider snapshot of every advancement currently known to the player. */
public record AdvancementSnapshot(
    int knownCount, int scanLimit, boolean truncated, List<AdvancementInfo> advancements) {
  public AdvancementSnapshot {
    if (knownCount < 0 || scanLimit <= 0) {
      throw new IllegalArgumentException("knownCount must be non-negative and scanLimit positive");
    }
    advancements =
        ModelValidation.immutableList(advancements, "advancements").stream()
            .sorted(Comparator.comparing(AdvancementInfo::advancementId))
            .toList();
    if (advancements.size() > scanLimit || knownCount < advancements.size()) {
      throw new IllegalArgumentException("advancement snapshot counts are inconsistent");
    }
    if (truncated != (knownCount > advancements.size())) {
      throw new IllegalArgumentException("truncated must describe omitted known advancements");
    }
  }
}
