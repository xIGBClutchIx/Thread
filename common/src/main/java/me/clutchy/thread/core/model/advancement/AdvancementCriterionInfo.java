package me.clutchy.thread.core.model.advancement;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Detached completion state for one criterion in a known vanilla advancement. */
public record AdvancementCriterionInfo(String name, boolean completed, String obtainedAt) {
  public AdvancementCriterionInfo {
    name = ModelValidation.boundedNonBlank(name, "name", 256);
    obtainedAt = ModelValidation.optionalBoundedNonBlank(obtainedAt, "obtainedAt", 64);
    if (completed != (obtainedAt != null)) {
      throw new IllegalArgumentException(
          "completed criteria must have obtainedAt and incomplete criteria must not");
    }
  }
}
