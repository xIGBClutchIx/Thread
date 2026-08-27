package me.clutchy.thread.core.model.advancement;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Exact canonical advancement ID requested by the detailed advancement tool. */
public record AdvancementLookupQuery(String advancementId) {
  public AdvancementLookupQuery {
    advancementId = ModelValidation.registryId(advancementId, "advancementId");
  }
}
