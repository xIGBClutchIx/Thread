package me.clutchy.thread.core.model.advancement;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Compact advancement state returned by the bounded advancement-list tool. */
public record AdvancementSummary(
    String advancementId,
    String title,
    String description,
    boolean completed,
    double completionPercentage,
    int completedCriteria,
    int totalCriteria,
    int completedRequirements,
    int totalRequirements,
    String parentAdvancementId,
    String tabAdvancementId,
    String tabTitle,
    AdvancementDisplayType displayType,
    Boolean hidden,
    String firstProgressAt,
    String completedAt) {
  public AdvancementSummary {
    advancementId = ModelValidation.registryId(advancementId, "advancementId");
    title = ModelValidation.optionalBoundedNonBlank(title, "title", 512);
    description = ModelValidation.optionalBoundedNonBlank(description, "description", 2_048);
    ModelValidation.finite(completionPercentage, "completionPercentage");
    if (completionPercentage < 0 || completionPercentage > 100) {
      throw new IllegalArgumentException("completionPercentage must be between 0 and 100");
    }
    if (completedCriteria < 0 || totalCriteria < completedCriteria) {
      throw new IllegalArgumentException("criteria counts are inconsistent");
    }
    if (completedRequirements < 0 || totalRequirements < completedRequirements) {
      throw new IllegalArgumentException("requirement counts are inconsistent");
    }
    parentAdvancementId =
        ModelValidation.optionalRegistryId(parentAdvancementId, "parentAdvancementId");
    tabAdvancementId = ModelValidation.optionalRegistryId(tabAdvancementId, "tabAdvancementId");
    tabTitle = ModelValidation.optionalBoundedNonBlank(tabTitle, "tabTitle", 512);
    firstProgressAt =
        ModelValidation.optionalBoundedNonBlank(firstProgressAt, "firstProgressAt", 64);
    completedAt = ModelValidation.optionalBoundedNonBlank(completedAt, "completedAt", 64);
  }

  /** Creates the compact list representation without repeating every criterion. */
  public static AdvancementSummary from(AdvancementInfo info) {
    return new AdvancementSummary(
        info.advancementId(),
        info.title(),
        info.description(),
        info.completed(),
        info.completionPercentage(),
        info.completedCriteria(),
        info.totalCriteria(),
        info.completedRequirements(),
        info.totalRequirements(),
        info.parentAdvancementId(),
        info.tabAdvancementId(),
        info.tabTitle(),
        info.displayType(),
        info.hidden(),
        info.firstProgressAt(),
        info.completedAt());
  }
}
