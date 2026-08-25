package me.clutchy.thread.core.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.TreeMap;
import me.clutchy.thread.core.model.crafting.CraftingResult;
import me.clutchy.thread.core.model.crafting.IngredientAllocation;
import me.clutchy.thread.core.model.crafting.IngredientAvailability;
import me.clutchy.thread.core.model.crafting.RecipeCraftability;
import me.clutchy.thread.core.model.player.InventorySlotInfo;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.model.recipe.RecipeIngredientInfo;
import me.clutchy.thread.core.model.recipe.RecipeLookupQuery;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.tool.ToolResult;

/** Compares live recipe requirements with a detached player-inventory snapshot. */
public final class CraftingService {
  private static final Comparator<IngredientKey> INGREDIENT_ORDER =
      Comparator.comparingInt((IngredientKey value) -> value.itemIds().size())
          .thenComparing(value -> String.join("\u0000", value.itemIds()))
          .thenComparing(value -> String.join("\u0000", value.tagIds()));
  private static final Comparator<RecipeInfo> RECIPE_ORDER =
      Comparator.comparing(RecipeInfo::recipeId)
          .thenComparing(RecipeInfo::type)
          .thenComparingInt(recipe -> recipe.result().count())
          .thenComparing(CraftingService::ingredientSignature);

  private final PlayerProvider players;
  private final RecipeProvider recipes;

  /** Creates a transport-independent crafting service over existing provider contracts. */
  public CraftingService(PlayerProvider players, RecipeProvider recipes) {
    this.players = Objects.requireNonNull(players, "players");
    this.recipes = Objects.requireNonNull(recipes, "recipes");
  }

  /** Assesses one execution of every live recipe that produces the requested item. */
  public ToolResult<CraftingResult> assess(RecipeLookupQuery query) {
    Objects.requireNonNull(query, "query");
    ToolResult<List<RecipeInfo>> recipeResult = recipes.recipesFor(query.itemId());
    if (!recipeResult.successful()) {
      return ToolResult.failure(Objects.requireNonNull(recipeResult.error()));
    }

    List<RecipeInfo> matchingRecipes = Objects.requireNonNull(recipeResult.value());
    if (matchingRecipes.isEmpty()) {
      return ToolResult.success(new CraftingResult(query.itemId(), false, List.of()));
    }

    ToolResult<InventorySnapshot> inventoryResult = players.inventory();
    if (!inventoryResult.successful()) {
      return ToolResult.failure(Objects.requireNonNull(inventoryResult.error()));
    }

    Map<String, Integer> inventory =
        inventoryCounts(Objects.requireNonNull(inventoryResult.value()));
    List<RecipeInfo> orderedRecipes = orderedRecipes(matchingRecipes);
    List<RecipeCraftability> assessments = new ArrayList<>();
    for (int index = 0; index < orderedRecipes.size(); index++) {
      assessments.add(assessRecipe(index + 1, orderedRecipes.get(index), 1, inventory));
    }
    boolean craftable = assessments.stream().anyMatch(RecipeCraftability::craftable);
    return ToolResult.success(new CraftingResult(query.itemId(), craftable, assessments));
  }

  /** Returns recipe definitions in Thread's stable variant order. */
  public List<RecipeInfo> orderedRecipes(List<RecipeInfo> recipeDefinitions) {
    Objects.requireNonNull(recipeDefinitions, "recipeDefinitions");
    return recipeDefinitions.stream()
        .map(recipe -> Objects.requireNonNull(recipe, "recipeDefinitions entry"))
        .sorted(RECIPE_ORDER)
        .toList();
  }

  /**
   * Allocates available item counts to a fixed number of executions of one recipe.
   *
   * <p>The recursive planner uses this method so direct assessments and planned steps share the
   * same alternative-aware maximum-flow allocation.
   */
  public RecipeCraftability assessRecipe(
      int variant, RecipeInfo recipe, int executions, Map<String, Integer> availableItems) {
    Objects.requireNonNull(recipe, "recipe");
    Objects.requireNonNull(availableItems, "availableItems");
    if (executions <= 0) {
      throw new IllegalArgumentException("executions must be positive");
    }
    TreeMap<String, Integer> normalizedItems = new TreeMap<>();
    availableItems.forEach(
        (itemId, count) -> {
          Objects.requireNonNull(itemId, "availableItems key");
          Objects.requireNonNull(count, "availableItems value");
          if (count < 0) {
            throw new IllegalArgumentException("available item counts must not be negative");
          }
          normalizedItems.put(itemId, count);
        });
    List<NormalizedIngredient> ingredients =
        normalizedIngredients(recipe.ingredients(), executions);
    Allocation allocation = allocate(normalizedItems, ingredients);
    List<IngredientAvailability> availability = new ArrayList<>();
    for (int index = 0; index < ingredients.size(); index++) {
      NormalizedIngredient ingredient = ingredients.get(index);
      Map<String, Integer> assigned = allocation.byIngredient().get(index);
      List<IngredientAllocation> itemAllocations =
          assigned.entrySet().stream()
              .map(entry -> new IngredientAllocation(entry.getKey(), entry.getValue()))
              .toList();
      int available = assigned.values().stream().mapToInt(Integer::intValue).sum();
      availability.add(
          new IngredientAvailability(
              ingredient.key().itemIds(),
              ingredient.key().tagIds(),
              ingredient.required(),
              available,
              ingredient.required() - available,
              itemAllocations));
    }
    boolean craftable = availability.stream().allMatch(value -> value.missing() == 0);
    return new RecipeCraftability(
        variant,
        recipe.recipeId(),
        recipe.type(),
        Math.multiplyExact(recipe.result().count(), executions),
        craftable,
        availability);
  }

  private static Map<String, Integer> inventoryCounts(InventorySnapshot inventory) {
    TreeMap<String, Integer> counts = new TreeMap<>();
    for (InventorySlotInfo slot : inventory.slots()) {
      counts.merge(slot.stack().itemId(), slot.stack().count(), Math::addExact);
    }
    return Map.copyOf(counts);
  }

  private static List<NormalizedIngredient> normalizedIngredients(
      List<RecipeIngredientInfo> ingredients) {
    return normalizedIngredients(ingredients, 1);
  }

  private static List<NormalizedIngredient> normalizedIngredients(
      List<RecipeIngredientInfo> ingredients, int executions) {
    TreeMap<IngredientKey, Integer> required = new TreeMap<>(INGREDIENT_ORDER);
    for (RecipeIngredientInfo ingredient : ingredients) {
      IngredientKey key = new IngredientKey(ingredient.itemIds(), ingredient.tagIds());
      required.merge(key, Math.multiplyExact(ingredient.count(), executions), Math::addExact);
    }
    return required.entrySet().stream()
        .map(entry -> new NormalizedIngredient(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static Allocation allocate(
      Map<String, Integer> inventory, List<NormalizedIngredient> ingredients) {
    List<String> itemIds =
        inventory.entrySet().stream()
            .filter(entry -> entry.getValue() > 0)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    int source = 0;
    int firstItem = 1;
    int firstIngredient = firstItem + itemIds.size();
    int sink = firstIngredient + ingredients.size();
    FlowGraph graph = new FlowGraph(sink + 1);
    List<TrackedEdge> trackedEdges = new ArrayList<>();

    for (int itemIndex = 0; itemIndex < itemIds.size(); itemIndex++) {
      String itemId = itemIds.get(itemIndex);
      int itemNode = firstItem + itemIndex;
      graph.addEdge(source, itemNode, inventory.get(itemId));
      for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
        NormalizedIngredient ingredient = ingredients.get(ingredientIndex);
        if (ingredient.key().itemIds().contains(itemId)) {
          FlowEdge edge =
              graph.addEdge(itemNode, firstIngredient + ingredientIndex, ingredient.required());
          trackedEdges.add(new TrackedEdge(itemId, ingredientIndex, edge));
        }
      }
    }
    for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
      graph.addEdge(
          firstIngredient + ingredientIndex, sink, ingredients.get(ingredientIndex).required());
    }
    graph.maximumFlow(source, sink);

    List<Map<String, Integer>> byIngredient = new ArrayList<>();
    for (int index = 0; index < ingredients.size(); index++) {
      byIngredient.add(new TreeMap<>());
    }
    for (TrackedEdge tracked : trackedEdges) {
      int assigned = tracked.edge().flow();
      if (assigned > 0) {
        byIngredient.get(tracked.ingredientIndex()).put(tracked.itemId(), assigned);
      }
    }
    return new Allocation(byIngredient.stream().map(Map::copyOf).toList());
  }

  private static String ingredientSignature(RecipeInfo recipe) {
    return normalizedIngredients(recipe.ingredients()).stream()
        .map(
            ingredient ->
                String.join(",", ingredient.key().itemIds())
                    + "#"
                    + String.join(",", ingredient.key().tagIds())
                    + "#"
                    + ingredient.required())
        .reduce("", (left, right) -> left + "|" + right);
  }

  private record IngredientKey(List<String> itemIds, List<String> tagIds) {}

  private record NormalizedIngredient(IngredientKey key, int required) {}

  private record TrackedEdge(String itemId, int ingredientIndex, FlowEdge edge) {}

  private record Allocation(List<Map<String, Integer>> byIngredient) {}

  private static final class FlowGraph {
    private final List<List<FlowEdge>> edges;

    private FlowGraph(int nodes) {
      edges = new ArrayList<>(nodes);
      for (int index = 0; index < nodes; index++) {
        edges.add(new ArrayList<>());
      }
    }

    private FlowEdge addEdge(int from, int to, int capacity) {
      FlowEdge forward = new FlowEdge(to, edges.get(to).size(), capacity, capacity);
      FlowEdge reverse = new FlowEdge(from, edges.get(from).size(), 0, 0);
      edges.get(from).add(forward);
      edges.get(to).add(reverse);
      return forward;
    }

    private void maximumFlow(int source, int sink) {
      while (true) {
        int[] parentNode = new int[edges.size()];
        int[] parentEdge = new int[edges.size()];
        Arrays.fill(parentNode, -1);
        parentNode[source] = source;
        Queue<Integer> pending = new ArrayDeque<>();
        pending.add(source);
        while (!pending.isEmpty() && parentNode[sink] == -1) {
          int node = pending.remove();
          List<FlowEdge> outgoing = edges.get(node);
          for (int edgeIndex = 0; edgeIndex < outgoing.size(); edgeIndex++) {
            FlowEdge edge = outgoing.get(edgeIndex);
            if (edge.capacity() > 0 && parentNode[edge.to()] == -1) {
              parentNode[edge.to()] = node;
              parentEdge[edge.to()] = edgeIndex;
              pending.add(edge.to());
            }
          }
        }
        if (parentNode[sink] == -1) {
          return;
        }

        int amount = Integer.MAX_VALUE;
        for (int node = sink; node != source; node = parentNode[node]) {
          amount = Math.min(amount, edges.get(parentNode[node]).get(parentEdge[node]).capacity());
        }
        for (int node = sink; node != source; node = parentNode[node]) {
          FlowEdge edge = edges.get(parentNode[node]).get(parentEdge[node]);
          edge.removeCapacity(amount);
          edges.get(node).get(edge.reverseIndex()).addCapacity(amount);
        }
      }
    }
  }

  private static final class FlowEdge {
    private final int to;
    private final int reverseIndex;
    private final int originalCapacity;
    private int capacity;

    private FlowEdge(int to, int reverseIndex, int capacity, int originalCapacity) {
      this.to = to;
      this.reverseIndex = reverseIndex;
      this.capacity = capacity;
      this.originalCapacity = originalCapacity;
    }

    private int to() {
      return to;
    }

    private int reverseIndex() {
      return reverseIndex;
    }

    private int capacity() {
      return capacity;
    }

    private int flow() {
      return originalCapacity - capacity;
    }

    private void removeCapacity(int amount) {
      capacity -= amount;
    }

    private void addCapacity(int amount) {
      capacity += amount;
    }
  }
}
