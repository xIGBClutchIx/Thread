package me.clutchy.thread.core.model.advancement;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Detailed detached snapshot of one advancement known to the local player. */
public record AdvancementInfo(
    String advancementId,
    String title,
    String description,
    boolean completed,
    double completionPercentage,
    int completedCriteria,
    int totalCriteria,
    int completedRequirements,
    int totalRequirements,
    boolean criteriaTruncated,
    String parentAdvancementId,
    String tabAdvancementId,
    String tabTitle,
    AdvancementDisplayType displayType,
    Boolean hidden,
    String firstProgressAt,
    String completedAt,
    List<AdvancementCriterionInfo> criteria) {
  public static final int MAX_RETURNED_CRITERIA = 2_048;

  public AdvancementInfo {
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
    criteria =
        ModelValidation.immutableList(criteria, "criteria").stream()
            .sorted(Comparator.comparing(AdvancementCriterionInfo::name))
            .toList();
    if (criteria.size() > MAX_RETURNED_CRITERIA) {
      throw new IllegalArgumentException("criteria exceed the output safety limit");
    }
    if (criteria.size() > totalCriteria) {
      throw new IllegalArgumentException("criteria list must not exceed totalCriteria");
    }
    if (criteriaTruncated != (criteria.size() < totalCriteria)) {
      throw new IllegalArgumentException("criteriaTruncated must describe omitted criteria");
    }
    long returnedCompleted = criteria.stream().filter(AdvancementCriterionInfo::completed).count();
    if ((!criteriaTruncated && returnedCompleted != completedCriteria)
        || (criteriaTruncated && returnedCompleted > completedCriteria)) {
      throw new IllegalArgumentException(
          "completedCriteria is inconsistent with returned criteria");
    }
    Set<String> names = new HashSet<>();
    for (AdvancementCriterionInfo criterion : criteria) {
      if (!names.add(criterion.name())) {
        throw new IllegalArgumentException("criteria must not contain duplicate names");
      }
    }
  }
}
