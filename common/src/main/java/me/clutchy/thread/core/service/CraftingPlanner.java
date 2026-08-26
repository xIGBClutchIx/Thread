package me.clutchy.thread.core.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.model.crafting.CraftingPlan;
import me.clutchy.thread.core.model.crafting.CraftingPlanIssue;
import me.clutchy.thread.core.model.crafting.CraftingPlanIssueType;
import me.clutchy.thread.core.model.crafting.CraftingPlanStep;
import me.clutchy.thread.core.model.crafting.IngredientAllocation;
import me.clutchy.thread.core.model.crafting.IngredientAvailability;
import me.clutchy.thread.core.model.crafting.MissingMaterial;
import me.clutchy.thread.core.model.crafting.RecipeCraftability;
import me.clutchy.thread.core.model.player.InventorySlotInfo;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.model.recipe.RecipeLookupQuery;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.tool.ToolResult;

/** Builds bounded, deterministic recursive crafting plans from detached provider snapshots. */
public final class CraftingPlanner {
  static final int DEFAULT_MAX_DEPTH = 32;
  static final int DEFAULT_MAX_STEPS = 512;
  static final int DEFAULT_MAX_BRANCHES = 4096;
  private static final int MAX_REQUIRED_UNITS = 1_000_000;
  private static final int MAX_INGREDIENT_GROUPS = 512;
  private static final int MAX_ALTERNATIVES_PER_INGREDIENT = 512;

  private static final Comparator<Candidate> CANDIDATE_ORDER =
      Comparator.comparingInt((Candidate value) -> value.score().issues())
          .thenComparingInt(value -> value.score().unfulfilled())
          .thenComparingLong(value -> value.score().missingUnits())
          .thenComparingInt(value -> value.score().steps())
          .thenComparingInt(Candidate::order);

  private final PlayerProvider players;
  private final RecipeProvider recipes;
  private final CraftingService crafting;
  private final int maxDepth;
  private final int maxSteps;
  private final int maxBranches;

  /** Creates a planner with Thread's production recursion and work limits. */
  public CraftingPlanner(PlayerProvider players, RecipeProvider recipes, CraftingService crafting) {
    this(players, recipes, crafting, DEFAULT_MAX_DEPTH, DEFAULT_MAX_STEPS, DEFAULT_MAX_BRANCHES);
  }

  /** Creates a planner with explicit safety limits for focused boundary tests. */
  CraftingPlanner(
      PlayerProvider players,
      RecipeProvider recipes,
      CraftingService crafting,
      int maxDepth,
      int maxSteps,
      int maxBranches) {
    this.players = Objects.requireNonNull(players, "players");
    this.recipes = Objects.requireNonNull(recipes, "recipes");
    this.crafting = Objects.requireNonNull(crafting, "crafting");
    if (maxDepth <= 0 || maxSteps <= 0 || maxBranches <= 0) {
      throw new IllegalArgumentException("planning limits must be positive");
    }
    this.maxDepth = maxDepth;
    this.maxSteps = maxSteps;
    this.maxBranches = maxBranches;
  }

  /** Plans one requested item using a single main-inventory snapshot. */
  public ToolResult<CraftingPlan> plan(RecipeLookupQuery query) {
    Objects.requireNonNull(query, "query");
    ToolResult<InventorySnapshot> inventoryResult = players.inventory();
    if (!inventoryResult.successful()) {
      return ToolResult.failure(Objects.requireNonNull(inventoryResult.error()));
    }

    PlanState state = new PlanState(new SupplyLedger(inventoryCounts(inventoryResult.value())));
    PlannerRun run = new PlannerRun();
    Fulfillment root = resolveItem(query.itemId(), 1, List.of(), state, run);
    if (run.error != null) {
      return ToolResult.failure(run.error);
    }

    List<CraftingPlanStep> steps = new ArrayList<>();
    for (int index = 0; index < state.steps.size(); index++) {
      StepDraft step = state.steps.get(index);
      steps.add(
          new CraftingPlanStep(
              index + 1,
              step.itemId(),
              step.variant(),
              step.recipeId(),
              step.type(),
              step.executions(),
              step.resultCount(),
              step.ingredients()));
    }
    List<MissingMaterial> missing =
        state.missing.entrySet().stream()
            .map(entry -> new MissingMaterial(entry.getKey(), entry.getValue()))
            .toList();
    boolean craftable = missing.isEmpty() && state.issues.isEmpty() && root.fulfilled() == 1;
    return ToolResult.success(
        new CraftingPlan(
            query.itemId(),
            1,
            root.inventoryUsed(),
            craftable,
            maxDepth,
            steps,
            missing,
            state.issues));
  }

  private Fulfillment resolveItem(
      String itemId, int required, List<String> activePath, PlanState state, PlannerRun run) {
    if (required <= 0 || run.error != null) {
      return new Fulfillment(itemId, 0, 0);
    }
    run.branches++;
    if (run.branches > maxBranches) {
      state.addIssue(
          CraftingPlanIssueType.PLAN_LIMIT, itemId, required, append(activePath, itemId));
      return new Fulfillment(itemId, 0, 0);
    }

    Consumption existing = state.supplies.consume(itemId, required);
    int remaining = required - existing.total();
    if (remaining == 0) {
      return new Fulfillment(itemId, required, existing.inventory());
    }
    if (activePath.contains(itemId)) {
      state.addIssue(CraftingPlanIssueType.CYCLE, itemId, remaining, cycle(activePath, itemId));
      return new Fulfillment(itemId, existing.total(), existing.inventory());
    }
    if (activePath.size() >= maxDepth) {
      state.addIssue(
          CraftingPlanIssueType.MAX_DEPTH, itemId, remaining, append(activePath, itemId));
      return new Fulfillment(itemId, existing.total(), existing.inventory());
    }

    List<RecipeInfo> definitions = recipeDefinitions(itemId, run);
    if (run.error != null) {
      return new Fulfillment(itemId, existing.total(), existing.inventory());
    }
    if (definitions.isEmpty()) {
      if (!state.tryAddMissing(itemId, remaining)) {
        state.addIssue(
            CraftingPlanIssueType.PLAN_LIMIT, itemId, remaining, append(activePath, itemId));
        return new Fulfillment(itemId, existing.total(), existing.inventory());
      }
      return new Fulfillment(itemId, required, existing.inventory());
    }

    List<String> childPath = append(activePath, itemId);
    PlanState baseline = state.copy();
    List<Candidate> candidates = new ArrayList<>();
    for (int index = 0; index < definitions.size() && run.error == null; index++) {
      PlanState candidateState = baseline.copy();
      run.branches++;
      if (run.branches > maxBranches) {
        candidateState.addIssue(CraftingPlanIssueType.PLAN_LIMIT, itemId, remaining, childPath);
        candidates.add(
            new Candidate(
                candidateState,
                new Fulfillment(itemId, existing.total(), existing.inventory()),
                index,
                score(baseline, candidateState, remaining)));
        break;
      }
      int produced =
          planRecipe(
              itemId, remaining, index + 1, definitions.get(index), childPath, candidateState, run);
      Fulfillment fulfillment =
          new Fulfillment(itemId, existing.total() + produced, existing.inventory());
      candidates.add(
          new Candidate(
              candidateState,
              fulfillment,
              index,
              score(baseline, candidateState, remaining - produced)));
    }
    if (run.error != null || candidates.isEmpty()) {
      return new Fulfillment(itemId, existing.total(), existing.inventory());
    }
    Candidate selected = candidates.stream().min(CANDIDATE_ORDER).orElseThrow();
    state.replaceWith(selected.state());
    return selected.fulfillment();
  }

  private int planRecipe(
      String itemId,
      int required,
      int variant,
      RecipeInfo recipe,
      List<String> activePath,
      PlanState state,
      PlannerRun run) {
    if (state.steps.size() >= maxSteps) {
      state.addIssue(CraftingPlanIssueType.PLAN_LIMIT, itemId, required, activePath);
      return 0;
    }

    int executions;
    RecipeCraftability assessment;
    try {
      executions = Math.addExact(required, recipe.result().count() - 1) / recipe.result().count();
      if (scaledRequirementsExceedLimit(recipe, executions)) {
        state.addIssue(CraftingPlanIssueType.PLAN_LIMIT, itemId, required, activePath);
        return 0;
      }
      assessment = crafting.assessRecipe(variant, recipe, executions, state.supplies.counts());
    } catch (ArithmeticException exception) {
      state.addIssue(CraftingPlanIssueType.PLAN_LIMIT, itemId, required, activePath);
      return 0;
    }

    List<TreeMap<String, Integer>> allocatedByIngredient = new ArrayList<>();
    for (IngredientAvailability ingredient : assessment.ingredients()) {
      TreeMap<String, Integer> allocations = new TreeMap<>();
      for (IngredientAllocation allocation : ingredient.allocations()) {
        // Reserve the whole maximum-flow result before recursion. Otherwise an earlier missing
        // branch could consume supply that the allocator assigned to a later ingredient.
        state.supplies.consumeExact(allocation.itemId(), allocation.count());
        allocations.merge(allocation.itemId(), allocation.count(), Math::addExact);
      }
      allocatedByIngredient.add(allocations);
    }

    List<IngredientAvailability> plannedIngredients = new ArrayList<>();
    boolean inputsResolved = true;
    for (int ingredientIndex = 0;
        ingredientIndex < assessment.ingredients().size();
        ingredientIndex++) {
      IngredientAvailability ingredient = assessment.ingredients().get(ingredientIndex);
      TreeMap<String, Integer> allocations = allocatedByIngredient.get(ingredientIndex);
      int available = ingredient.available();
      int unresolved = ingredient.missing();
      if (unresolved > 0) {
        Candidate selected =
            selectAlternative(ingredient.itemIds(), unresolved, activePath, state, run);
        if (run.error != null) {
          return 0;
        }
        state.replaceWith(selected.state());
        int supplied = selected.fulfillment().fulfilled();
        if (supplied > 0) {
          allocations.merge(selected.fulfillment().itemId(), supplied, Math::addExact);
          available = Math.addExact(available, supplied);
          unresolved -= supplied;
        }
      }
      if (unresolved > 0) {
        inputsResolved = false;
      }
      List<IngredientAllocation> itemAllocations =
          allocations.entrySet().stream()
              .map(entry -> new IngredientAllocation(entry.getKey(), entry.getValue()))
              .toList();
      plannedIngredients.add(
          new IngredientAvailability(
              ingredient.itemIds(),
              ingredient.tagIds(),
              ingredient.required(),
              available,
              unresolved,
              itemAllocations));
    }

    if (state.steps.size() >= maxSteps) {
      state.addIssue(CraftingPlanIssueType.PLAN_LIMIT, itemId, required, activePath);
      return 0;
    }
    state.steps.add(
        new StepDraft(
            itemId,
            variant,
            recipe.recipeId(),
            recipe.type(),
            executions,
            assessment.resultCount(),
            plannedIngredients));
    if (!inputsResolved) {
      return 0;
    }
    state.supplies.addCrafted(itemId, assessment.resultCount());
    return state.supplies.consume(itemId, required).total();
  }

  private Candidate selectAlternative(
      List<String> itemIds,
      int required,
      List<String> activePath,
      PlanState state,
      PlannerRun run) {
    PlanState baseline = state.copy();
    List<Candidate> candidates = new ArrayList<>();
    for (int index = 0; index < itemIds.size() && run.error == null; index++) {
      PlanState candidateState = baseline.copy();
      Fulfillment fulfillment =
          resolveItem(itemIds.get(index), required, activePath, candidateState, run);
      candidates.add(
          new Candidate(
              candidateState,
              fulfillment,
              index,
              score(baseline, candidateState, required - fulfillment.fulfilled())));
      if (run.branches > maxBranches) {
        break;
      }
    }
    if (candidates.isEmpty()) {
      return new Candidate(
          baseline, new Fulfillment(itemIds.getFirst(), 0, 0), 0, new Score(1, required, 0, 0));
    }
    return candidates.stream().min(CANDIDATE_ORDER).orElseThrow();
  }

  private List<RecipeInfo> recipeDefinitions(String itemId, PlannerRun run) {
    ToolResult<List<RecipeInfo>> cached = run.recipeCache.get(itemId);
    if (cached == null) {
      cached = recipes.recipesFor(itemId);
      run.recipeCache.put(itemId, cached);
    }
    if (!cached.successful()) {
      run.error = Objects.requireNonNull(cached.error());
      return List.of();
    }
    return crafting.orderedRecipes(Objects.requireNonNull(cached.value()));
  }

  private static Score score(PlanState before, PlanState after, int unfulfilled) {
    return new Score(
        after.issues.size() - before.issues.size(),
        unfulfilled,
        after.missingUnits() - before.missingUnits(),
        after.steps.size() - before.steps.size());
  }

  private static boolean scaledRequirementsExceedLimit(RecipeInfo recipe, int executions) {
    if (recipe.ingredients().size() > MAX_INGREDIENT_GROUPS) {
      return true;
    }
    long units = 0;
    for (var ingredient : recipe.ingredients()) {
      if (ingredient.itemIds().size() > MAX_ALTERNATIVES_PER_INGREDIENT) {
        return true;
      }
      units += (long) ingredient.count() * executions;
      if (units > MAX_REQUIRED_UNITS) {
        return true;
      }
    }
    return (long) recipe.result().count() * executions > MAX_REQUIRED_UNITS;
  }

  private static Map<String, Integer> inventoryCounts(InventorySnapshot inventory) {
    TreeMap<String, Integer> counts = new TreeMap<>();
    for (InventorySlotInfo slot : Objects.requireNonNull(inventory, "inventory").slots()) {
      counts.merge(slot.stack().itemId(), slot.stack().count(), Math::addExact);
    }
    return counts;
  }

  private static List<String> append(List<String> path, String itemId) {
    ArrayList<String> result = new ArrayList<>(path);
    result.add(itemId);
    return List.copyOf(result);
  }

  private static List<String> cycle(List<String> path, String itemId) {
    int start = path.indexOf(itemId);
    ArrayList<String> result = new ArrayList<>(path.subList(start, path.size()));
    result.add(itemId);
    return List.copyOf(result);
  }

  private record Fulfillment(String itemId, int fulfilled, int inventoryUsed) {}

  private record Score(int issues, int unfulfilled, long missingUnits, int steps) {}

  private record Candidate(PlanState state, Fulfillment fulfillment, int order, Score score) {}

  private record StepDraft(
      String itemId,
      int variant,
      String recipeId,
      String type,
      int executions,
      int resultCount,
      List<IngredientAvailability> ingredients) {}

  private static final class PlannerRun {
    private final Map<String, ToolResult<List<RecipeInfo>>> recipeCache = new HashMap<>();
    private int branches;
    private ToolError error;
  }

  private static final class PlanState {
    private SupplyLedger supplies;
    private List<StepDraft> steps;
    private TreeMap<String, Integer> missing;
    private List<CraftingPlanIssue> issues;

    private PlanState(SupplyLedger supplies) {
      this(supplies, new ArrayList<>(), new TreeMap<>(), new ArrayList<>());
    }

    private PlanState(
        SupplyLedger supplies,
        List<StepDraft> steps,
        TreeMap<String, Integer> missing,
        List<CraftingPlanIssue> issues) {
      this.supplies = supplies;
      this.steps = steps;
      this.missing = missing;
      this.issues = issues;
    }

    private PlanState copy() {
      return new PlanState(
          supplies.copy(), new ArrayList<>(steps), new TreeMap<>(missing), new ArrayList<>(issues));
    }

    private void replaceWith(PlanState source) {
      supplies = source.supplies.copy();
      steps = new ArrayList<>(source.steps);
      missing = new TreeMap<>(source.missing);
      issues = new ArrayList<>(source.issues);
    }

    private boolean tryAddMissing(String itemId, int count) {
      if (count > MAX_REQUIRED_UNITS || missingUnits() + count > MAX_REQUIRED_UNITS) {
        return false;
      }
      missing.merge(itemId, count, Math::addExact);
      return true;
    }

    private void addIssue(
        CraftingPlanIssueType type, String itemId, int required, List<String> path) {
      issues.add(new CraftingPlanIssue(type, itemId, required, path));
    }

    private long missingUnits() {
      return missing.values().stream().mapToLong(Integer::longValue).sum();
    }
  }

  private static final class SupplyLedger {
    private final TreeMap<String, Integer> inventory;
    private final TreeMap<String, Integer> crafted;

    private SupplyLedger(Map<String, Integer> inventory) {
      this(new TreeMap<>(inventory), new TreeMap<>());
    }

    private SupplyLedger(TreeMap<String, Integer> inventory, TreeMap<String, Integer> crafted) {
      this.inventory = inventory;
      this.crafted = crafted;
    }

    private SupplyLedger copy() {
      return new SupplyLedger(new TreeMap<>(inventory), new TreeMap<>(crafted));
    }

    private Map<String, Integer> counts() {
      TreeMap<String, Integer> counts = new TreeMap<>(inventory);
      crafted.forEach((itemId, count) -> counts.merge(itemId, count, Math::addExact));
      return counts;
    }

    private Consumption consume(String itemId, int requested) {
      int fromInventory = take(inventory, itemId, requested);
      int fromCrafting = take(crafted, itemId, requested - fromInventory);
      return new Consumption(fromInventory, fromCrafting);
    }

    private void consumeExact(String itemId, int requested) {
      Consumption consumed = consume(itemId, requested);
      if (consumed.total() != requested) {
        throw new IllegalStateException("allocation exceeded available supply for " + itemId);
      }
    }

    private void addCrafted(String itemId, int count) {
      crafted.merge(itemId, count, Math::addExact);
    }

    private static int take(Map<String, Integer> source, String itemId, int requested) {
      int available = source.getOrDefault(itemId, 0);
      int consumed = Math.min(available, requested);
      int remaining = available - consumed;
      if (remaining == 0) {
        source.remove(itemId);
      } else {
        source.put(itemId, remaining);
      }
      return consumed;
    }
  }

  private record Consumption(int inventory, int crafted) {
    private int total() {
      return Math.addExact(inventory, crafted);
    }
  }
}
