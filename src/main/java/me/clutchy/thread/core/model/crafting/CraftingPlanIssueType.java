package me.clutchy.thread.core.model.crafting;

/** Safety boundary that stopped resolution of one crafting-plan branch. */
public enum CraftingPlanIssueType {
  CYCLE,
  MAX_DEPTH,
  PLAN_LIMIT
}
