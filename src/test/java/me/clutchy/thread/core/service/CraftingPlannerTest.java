package me.clutchy.thread.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.crafting.CraftingPlan;
import me.clutchy.thread.core.model.crafting.CraftingPlanIssueType;
import me.clutchy.thread.core.model.item.ItemInfo;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySlotInfo;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.model.recipe.RecipeIngredientInfo;
import me.clutchy.thread.core.model.recipe.RecipeLookupQuery;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class CraftingPlannerTest {
  private static final String TARGET = "test:target";

  @Test
  void plansSimpleRecursiveRecipe() {
    CraftingPlan plan =
        plan(
            inventory(stack("test:raw", 1)),
            recipe(TARGET, "test:target_recipe", ingredient("test:component", 1)),
            recipe("test:component", "test:component_recipe", ingredient("test:raw", 1)));

    assertTrue(plan.craftable());
    assertEquals(List.of("test:component", TARGET), stepItems(plan));
    assertTrue(plan.missingMaterials().isEmpty());
    assertTrue(plan.issues().isEmpty());
  }

  @Test
  void plansMultipleNestingLevelsInDependencyOrder() {
    CraftingPlan plan =
        plan(
            inventory(stack("test:raw", 1)),
            recipe(TARGET, "test:target_recipe", ingredient("test:middle", 1)),
            recipe("test:middle", "test:middle_recipe", ingredient("test:part", 1)),
            recipe("test:part", "test:part_recipe", ingredient("test:raw", 1)));

    assertEquals(List.of("test:part", "test:middle", TARGET), stepItems(plan));
    assertEquals(List.of(1, 2, 3), plan.steps().stream().map(step -> step.step()).toList());
  }

  @Test
  void reportsOnlyTheRawRemainderAfterPartialInventoryAllocation() {
    CraftingPlan plan =
        plan(
            inventory(stack("test:raw", 2)),
            recipe(TARGET, "test:target_recipe", ingredient("test:raw", 3)));

    assertFalse(plan.craftable());
    assertEquals("test:raw", plan.missingMaterials().getFirst().itemId());
    assertEquals(1, plan.missingMaterials().getFirst().count());
    assertEquals(3, plan.steps().getFirst().ingredients().getFirst().available());
    assertEquals(
        3, plan.steps().getFirst().ingredients().getFirst().allocations().getFirst().count());
  }

  @Test
  void sharesOneInventoryLedgerAcrossRepeatedIngredientsInSeparateBranches() {
    CraftingPlan plan =
        plan(
            inventory(stack("test:raw", 1)),
            recipe(
                TARGET,
                "test:target_recipe",
                ingredient("test:left", 1),
                ingredient("test:right", 1)),
            recipe("test:left", "test:left_recipe", ingredient("test:raw", 1)),
            recipe("test:right", "test:right_recipe", ingredient("test:raw", 1)));

    assertEquals(1, plan.missingMaterials().getFirst().count());
    assertEquals(List.of("test:left", "test:right", TARGET), stepItems(plan));
  }

  @Test
  void reservesParentAllocationsBeforeResolvingMissingSubrecipes() {
    CraftingPlan plan =
        plan(
            inventory(stack("test:raw", 1)),
            recipe(
                TARGET,
                "test:target_recipe",
                ingredient("test:component", 1),
                ingredient("test:raw", 1)),
            recipe("test:component", "test:component_recipe", ingredient("test:raw", 1)));

    assertEquals(1, plan.missingMaterials().getFirst().count());
    assertEquals(
        1,
        plan.steps().getLast().ingredients().stream()
            .filter(ingredient -> ingredient.itemIds().equals(List.of("test:raw")))
            .findFirst()
            .orElseThrow()
            .available());
  }

  @Test
  void selectsTheDeterministicVariantWithTheFewestRawShortages() {
    CraftingPlan plan =
        plan(
            inventory(stack("test:present", 1)),
            recipe(TARGET, "test:a_missing_recipe", ingredient("test:absent", 1)),
            recipe(TARGET, "test:b_present_recipe", ingredient("test:present", 1)));

    assertTrue(plan.craftable());
    assertEquals("test:b_present_recipe", plan.steps().getFirst().recipeId());
    assertEquals(2, plan.steps().getFirst().variant());
  }

  @Test
  void terminatesAndReportsADirectCycle() {
    CraftingPlan plan =
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
            Duration.ofSeconds(1),
            () -> plan(inventory(), recipe(TARGET, "test:self_recipe", ingredient(TARGET, 1))));

    assertFalse(plan.craftable());
    assertEquals(CraftingPlanIssueType.CYCLE, plan.issues().getFirst().type());
    assertEquals(List.of(TARGET, TARGET), plan.issues().getFirst().path());
  }

  @Test
  void terminatesAndReportsAnIndirectCycle() {
    CraftingPlan plan =
        plan(
            inventory(),
            recipe(TARGET, "test:target_recipe", ingredient("test:middle", 1)),
            recipe("test:middle", "test:middle_recipe", ingredient(TARGET, 1)));

    assertEquals(CraftingPlanIssueType.CYCLE, plan.issues().getFirst().type());
    assertEquals(List.of(TARGET, "test:middle", TARGET), plan.issues().getFirst().path());
  }

  @Test
  void detectsCyclesAcrossResolvedTagAlternativesAndPreservesTheTag() {
    RecipeIngredientInfo tagged =
        new RecipeIngredientInfo(
            List.of("test:branch_b", "test:branch_c"), List.of("test:loop_members"), 1);
    CraftingPlan plan =
        plan(
            inventory(),
            recipe(TARGET, "test:tagged_recipe", tagged),
            recipe("test:branch_b", "test:b_recipe", ingredient(TARGET, 1)),
            recipe("test:branch_c", "test:c_recipe", ingredient(TARGET, 1)));

    assertEquals(CraftingPlanIssueType.CYCLE, plan.issues().getFirst().type());
    assertEquals(List.of(TARGET, "test:branch_b", TARGET), plan.issues().getFirst().path());
    assertEquals(
        List.of("test:loop_members"), plan.steps().getLast().ingredients().getFirst().tagIds());
  }

  @Test
  void prefersANonCyclicRecipeVariant() {
    CraftingPlan plan =
        plan(
            inventory(stack("test:raw", 1)),
            recipe(TARGET, "test:a_cycle", ingredient(TARGET, 1)),
            recipe(TARGET, "test:b_safe", ingredient("test:raw", 1)));

    assertTrue(plan.craftable());
    assertTrue(plan.issues().isEmpty());
    assertEquals("test:b_safe", plan.steps().getFirst().recipeId());
  }

  @Test
  void stopsADeepAcyclicGraphAtTheConfiguredMaximumDepth() {
    MapRecipeProvider provider =
        provider(
            recipe(TARGET, "test:target_recipe", ingredient("test:middle", 1)),
            recipe("test:middle", "test:middle_recipe", ingredient("test:deep", 1)),
            recipe("test:deep", "test:deep_recipe", ingredient("test:raw", 1)));
    CraftingPlan plan = plan(inventory(), provider, 2);

    assertEquals(CraftingPlanIssueType.MAX_DEPTH, plan.issues().getFirst().type());
    assertEquals(List.of(TARGET, "test:middle", "test:deep"), plan.issues().getFirst().path());
  }

  @Test
  void stopsPathologicalScaledQuantitiesAtThePlanLimit() {
    CraftingPlan plan =
        plan(inventory(), recipe(TARGET, "test:huge_recipe", ingredient("test:raw", 1_000_001)));

    assertFalse(plan.craftable());
    assertEquals(CraftingPlanIssueType.PLAN_LIMIT, plan.issues().getFirst().type());
    assertTrue(plan.missingMaterials().isEmpty());
  }

  @Test
  void repeatedRecipeIdsStillReceiveStableVariantsAndSelection() {
    RecipeInfo first = recipe(TARGET, "test:duplicate", ingredient("test:absent", 1));
    RecipeInfo second = recipe(TARGET, "test:duplicate", ingredient("test:present", 1));

    CraftingPlan forward = plan(inventory(stack("test:present", 1)), first, second);
    CraftingPlan reverse = plan(inventory(stack("test:present", 1)), second, first);

    assertEquals(forward, reverse);
    assertEquals(2, forward.steps().getFirst().variant());
  }

  @Test
  void emptyInventoryAndNoRecipeReturnTheTargetAsMissingRawMaterial() {
    CraftingPlan plan = plan(inventory());

    assertFalse(plan.craftable());
    assertTrue(plan.steps().isEmpty());
    assertEquals(TARGET, plan.missingMaterials().getFirst().itemId());
    assertEquals(1, plan.missingMaterials().getFirst().count());
  }

  @Test
  void propagatesNoWorldAndMultiplayerInventoryFailures() {
    assertEquals(
        ToolErrorCode.WORLD_NOT_AVAILABLE,
        planFailure(ToolErrorCode.WORLD_NOT_AVAILABLE).error().code());
    assertEquals(ToolErrorCode.UNSUPPORTED, planFailure(ToolErrorCode.UNSUPPORTED).error().code());
  }

  private static ToolResult<CraftingPlan> planFailure(ToolErrorCode code) {
    PlayerProvider player = new FailingPlayerProvider(code);
    MapRecipeProvider provider = provider();
    CraftingService service = new CraftingService(player, provider);
    return new CraftingPlanner(player, provider, service).plan(new RecipeLookupQuery(TARGET));
  }

  private static CraftingPlan plan(InventorySnapshot inventory, RecipeInfo... recipes) {
    return plan(inventory, provider(recipes), CraftingPlanner.DEFAULT_MAX_DEPTH);
  }

  private static CraftingPlan plan(
      InventorySnapshot inventory, MapRecipeProvider provider, int maxDepth) {
    FakePlayerProvider player = new FakePlayerProvider(inventory);
    CraftingService service = new CraftingService(player, provider);
    ToolResult<CraftingPlan> result =
        new CraftingPlanner(player, provider, service, maxDepth, 64, 512)
            .plan(new RecipeLookupQuery(TARGET));
    assertTrue(result.successful(), () -> String.valueOf(result.error()));
    return result.value();
  }

  private static List<String> stepItems(CraftingPlan plan) {
    return plan.steps().stream().map(step -> step.itemId()).toList();
  }

  private static MapRecipeProvider provider(RecipeInfo... recipes) {
    Map<String, List<RecipeInfo>> byResult = new HashMap<>();
    for (RecipeInfo recipe : recipes) {
      byResult.computeIfAbsent(recipe.result().itemId(), ignored -> new ArrayList<>()).add(recipe);
    }
    return new MapRecipeProvider(byResult);
  }

  private static RecipeIngredientInfo ingredient(String itemId, int count) {
    return new RecipeIngredientInfo(List.of(itemId), List.of(), count);
  }

  private static RecipeInfo recipe(
      String resultItemId, String recipeId, RecipeIngredientInfo... ingredients) {
    return new RecipeInfo(
        recipeId, "minecraft:crafting_shaped", item(resultItemId, 1), List.of(ingredients));
  }

  private static InventorySnapshot inventory(ItemStackInfo... stacks) {
    List<InventorySlotInfo> slots = new ArrayList<>();
    for (int index = 0; index < stacks.length; index++) {
      slots.add(new InventorySlotInfo(index, stacks[index]));
    }
    return new InventorySnapshot(0, slots);
  }

  private static ItemStackInfo stack(String itemId, int count) {
    return item(itemId, count);
  }

  private static ItemStackInfo item(String itemId, int count) {
    return new ItemStackInfo(itemId, itemId, null, count, 64, null, List.of(), null);
  }

  private record MapRecipeProvider(Map<String, List<RecipeInfo>> recipes)
      implements RecipeProvider {
    @Override
    public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
      return ToolResult.success(recipes.getOrDefault(itemId, List.of()));
    }

    @Override
    public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
      return ToolResult.success(new ItemSearchResult(query, limit, false, List.<ItemInfo>of()));
    }
  }

  private record FakePlayerProvider(InventorySnapshot snapshot) implements PlayerProvider {
    @Override
    public ToolResult<PlayerStatus> status() {
      throw new AssertionError("status should not be read by crafting planning");
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
      return ToolResult.success(snapshot);
    }

    @Override
    public ToolResult<EquipmentSnapshot> equipment() {
      throw new AssertionError("equipment should not be read by crafting planning");
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      throw new AssertionError("target block should not be read by crafting planning");
    }
  }

  private static final class FailingPlayerProvider implements PlayerProvider {
    private final ToolError error;

    private FailingPlayerProvider(ToolErrorCode code) {
      error = ToolError.of(code, "Unavailable for test.", true);
    }

    @Override
    public ToolResult<PlayerStatus> status() {
      throw new AssertionError("status should not be read by crafting planning");
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<EquipmentSnapshot> equipment() {
      throw new AssertionError("equipment should not be read by crafting planning");
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      throw new AssertionError("target block should not be read by crafting planning");
    }
  }
}
