package me.clutchy.thread.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.clutchy.thread.core.model.crafting.CraftingPlan;
import me.clutchy.thread.core.model.crafting.CraftingQuery;
import me.clutchy.thread.core.model.crafting.CraftingResult;
import me.clutchy.thread.core.model.crafting.CraftingScope;
import me.clutchy.thread.core.model.crafting.IngredientAllocation;
import me.clutchy.thread.core.model.crafting.IngredientAvailability;
import me.clutchy.thread.core.model.item.ItemInfo;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.item.find.FoundItemSourceType;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySlotInfo;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.model.recipe.RecipeIngredientInfo;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockEntityItemInfo;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.ContainerInspectionQuery;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyContainerSnapshotResult;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.service.item.CraftingItemSourceProvider;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class StorageAwareCraftingTest {
  private static final String PICKAXE = "test:pickaxe";

  @Test
  void expandedScopeCombinesPlayerAndMultipleContainersWithDeterministicSources() {
    CountingPlayer player =
        new CountingPlayer(inventory(stack("test:diamond", 1), stack("test:stick", 1)));
    CountingWorld world =
        new CountingWorld(
            false,
            List.of(
                container(
                    "minecraft:barrel",
                    new BlockPosition(3, 64, 0),
                    3,
                    Map.of("contentsResolved", "true"),
                    slot("4", "test:diamond", 1)),
                container(
                    "minecraft:chest",
                    new BlockPosition(2, 64, 0),
                    2,
                    Map.of("contentsResolved", "true"),
                    slot("1", "test:diamond", 1),
                    slot("2", "test:stick", 1))));
    MapRecipeProvider recipes =
        provider(
            recipe(
                PICKAXE,
                1,
                "test:pickaxe_recipe",
                ingredient("test:diamond", 3),
                ingredient("test:stick", 2)));
    CraftingService service = service(player, world, recipes);

    CraftingResult result =
        service.assess(new CraftingQuery(PICKAXE, CraftingScope.PLAYER_AND_NEARBY)).value();

    assertTrue(result.craftable());
    assertEquals(CraftingScope.PLAYER_AND_NEARBY, result.scope());
    assertTrue(result.sourceStatus().complete());
    assertEquals(1, player.inventoryReads);
    assertEquals(1, world.snapshotReads);
    assertEquals(new NearbyContainerQuery(8, 4), world.lastQuery);

    IngredientAllocation diamonds = availability(result, "test:diamond").allocations().getFirst();
    assertEquals(3, diamonds.count());
    assertEquals(
        List.of(
            FoundItemSourceType.PLAYER_INVENTORY,
            FoundItemSourceType.NEARBY_CONTAINER,
            FoundItemSourceType.NEARBY_CONTAINER),
        diamonds.sourceAllocations().stream().map(source -> source.sourceType()).toList());
    assertEquals(
        java.util.Arrays.asList(null, new BlockPosition(2, 64, 0), new BlockPosition(3, 64, 0)),
        diamonds.sourceAllocations().stream().map(source -> source.containerPosition()).toList());
    assertEquals(3, diamonds.sourceAllocations().stream().mapToInt(source -> source.count()).sum());
  }

  @Test
  void playerOnlyNeverReadsNearbySourcesAndPreservesDuplicateAllocationSafety() {
    CountingPlayer player =
        new CountingPlayer(inventory(stack("test:diamond", 1), stack("test:gold", 1)));
    CountingWorld world =
        new CountingWorld(
            false,
            List.of(
                container(
                    "minecraft:chest",
                    new BlockPosition(1, 64, 0),
                    1,
                    Map.of("contentsResolved", "true"),
                    slot("0", "test:diamond", 64))));
    RecipeIngredientInfo alternatives =
        new RecipeIngredientInfo(List.of("test:diamond", "test:gold"), List.of("test:precious"), 2);
    MapRecipeProvider recipes =
        provider(recipe(PICKAXE, 1, "test:overlap", ingredient("test:diamond", 1), alternatives));
    CraftingService service = service(player, world, recipes);

    CraftingResult result =
        service.assess(new CraftingQuery(PICKAXE, CraftingScope.PLAYER_ONLY)).value();

    assertFalse(result.craftable());
    assertEquals(0, world.snapshotReads);
    assertEquals(
        2,
        result.recipes().getFirst().ingredients().stream()
            .mapToInt(IngredientAvailability::available)
            .sum());
    assertEquals(
        2,
        result.recipes().getFirst().ingredients().stream()
            .flatMap(ingredient -> ingredient.allocations().stream())
            .mapToInt(IngredientAllocation::count)
            .sum());
  }

  @Test
  void expandedScopeSurfacesTruncationAndExcludesUnresolvedOrLimitedContents() {
    CountingPlayer player = new CountingPlayer(inventory());
    CountingWorld world =
        new CountingWorld(
            true,
            List.of(
                container(
                    "minecraft:chest",
                    new BlockPosition(1, 64, 0),
                    1,
                    Map.of("contentsResolved", "true"),
                    slot("0", "test:diamond", 1)),
                container(
                    "minecraft:chest",
                    new BlockPosition(2, 64, 0),
                    2,
                    Map.of("contentsResolved", "false"),
                    slot("0", "test:diamond", 64)),
                container(
                    "minecraft:barrel",
                    new BlockPosition(3, 64, 0),
                    3,
                    Map.of("contentsResolved", "true", "itemsTruncated", "true"),
                    slot("0", "test:diamond", 64))));
    MapRecipeProvider recipes =
        provider(recipe(PICKAXE, 1, "test:bounded", ingredient("test:diamond", 2)));

    CraftingResult result =
        service(player, world, recipes)
            .assess(new CraftingQuery(PICKAXE, CraftingScope.PLAYER_AND_NEARBY))
            .value();

    assertFalse(result.craftable());
    assertFalse(result.sourceStatus().complete());
    assertTrue(result.sourceStatus().nearbyContainersTruncated());
    assertEquals(1, result.sourceStatus().unresolvedContainersSkipped());
    assertEquals(1, result.sourceStatus().contentLimitedContainersSkipped());
    assertEquals(1, availability(result, "test:diamond").available());
    assertEquals(1, availability(result, "test:diamond").missing());
  }

  @Test
  void recursivePlanUsesOneExpandedSnapshotAndReportsContainerProvenance() {
    CountingPlayer player = new CountingPlayer(inventory());
    CountingWorld world =
        new CountingWorld(
            false,
            List.of(
                container(
                    "minecraft:chest",
                    new BlockPosition(2, 64, 1),
                    2.2,
                    Map.of("contentsResolved", "true"),
                    slot("7", "test:log", 1))));
    MapRecipeProvider recipes =
        provider(
            recipe("test:table", 1, "test:table_recipe", ingredient("test:plank", 4)),
            recipe("test:plank", 4, "test:plank_recipe", ingredient("test:log", 1)));
    CraftingItemSourceProvider sources = sources(player, world);
    CraftingService service = new CraftingService(sources, recipes);
    CraftingPlanner planner = new CraftingPlanner(sources, recipes, service);

    CraftingPlan playerOnly =
        planner.plan(new CraftingQuery("test:table", CraftingScope.PLAYER_ONLY)).value();
    assertFalse(playerOnly.craftable());
    assertEquals(0, world.snapshotReads);

    player.inventoryReads = 0;
    CraftingPlan expanded =
        planner.plan(new CraftingQuery("test:table", CraftingScope.PLAYER_AND_NEARBY)).value();

    assertTrue(expanded.craftable());
    assertEquals(1, player.inventoryReads);
    assertEquals(1, world.snapshotReads);
    assertEquals(
        List.of("test:plank", "test:table"),
        expanded.steps().stream().map(step -> step.itemId()).toList());
    IngredientAllocation log =
        expanded.steps().getFirst().ingredients().getFirst().allocations().getFirst();
    assertEquals(1, log.sourceAllocations().size());
    assertEquals(
        new BlockPosition(2, 64, 1), log.sourceAllocations().getFirst().containerPosition());
    assertTrue(
        expanded
            .steps()
            .getLast()
            .ingredients()
            .getFirst()
            .allocations()
            .getFirst()
            .sourceAllocations()
            .isEmpty());
  }

  private static CraftingService service(
      CountingPlayer player, CountingWorld world, MapRecipeProvider recipes) {
    return new CraftingService(sources(player, world), recipes);
  }

  private static CraftingItemSourceProvider sources(CountingPlayer player, CountingWorld world) {
    return CraftingItemSourceProvider.vanilla(player, world, new NearbyContainerQuery(8, 4));
  }

  private static IngredientAvailability availability(CraftingResult result, String itemId) {
    return result.recipes().getFirst().ingredients().stream()
        .filter(ingredient -> ingredient.itemIds().contains(itemId))
        .findFirst()
        .orElseThrow();
  }

  private static InventorySnapshot inventory(ItemStackInfo... stacks) {
    List<InventorySlotInfo> slots = new ArrayList<>();
    for (int index = 0; index < stacks.length; index++) {
      slots.add(new InventorySlotInfo(index, stacks[index]));
    }
    return new InventorySnapshot(0, slots);
  }

  private static BlockInfo container(
      String typeId,
      BlockPosition position,
      double distance,
      Map<String, String> state,
      BlockEntityItemInfo... items) {
    return new BlockInfo(
        typeId,
        typeId,
        position,
        Map.of(),
        distance,
        true,
        new BlockEntityInfo(typeId, 27, List.of(items), state));
  }

  private static BlockEntityItemInfo slot(String slot, String itemId, int count) {
    return new BlockEntityItemInfo(slot, stack(itemId, count));
  }

  private static ItemStackInfo stack(String itemId, int count) {
    return new ItemStackInfo(itemId, itemId, null, count, 64, null, List.of(), null);
  }

  private static RecipeIngredientInfo ingredient(String itemId, int count) {
    return new RecipeIngredientInfo(List.of(itemId), List.of(), count);
  }

  private static RecipeInfo recipe(
      String resultItemId, int resultCount, String recipeId, RecipeIngredientInfo... ingredients) {
    return new RecipeInfo(
        recipeId,
        "minecraft:crafting_shaped",
        stack(resultItemId, resultCount),
        List.of(ingredients));
  }

  private static MapRecipeProvider provider(RecipeInfo... recipes) {
    Map<String, List<RecipeInfo>> byResult = new HashMap<>();
    for (RecipeInfo recipe : recipes) {
      byResult.computeIfAbsent(recipe.result().itemId(), ignored -> new ArrayList<>()).add(recipe);
    }
    return new MapRecipeProvider(byResult);
  }

  private static final class CountingPlayer implements PlayerProvider {
    private final InventorySnapshot inventory;
    private int inventoryReads;

    private CountingPlayer(InventorySnapshot inventory) {
      this.inventory = inventory;
    }

    @Override
    public ToolResult<PlayerStatus> status() {
      throw new AssertionError("status was not expected");
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
      inventoryReads++;
      return ToolResult.success(inventory);
    }

    @Override
    public ToolResult<EquipmentSnapshot> equipment() {
      throw new AssertionError("equipment is not eligible for crafting");
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      throw new AssertionError("target block was not expected");
    }
  }

  private static final class CountingWorld implements WorldProvider {
    private final boolean truncated;
    private final List<BlockInfo> containers;
    private int snapshotReads;
    private NearbyContainerQuery lastQuery;

    private CountingWorld(boolean truncated, List<BlockInfo> containers) {
      this.truncated = truncated;
      this.containers = containers;
    }

    @Override
    public ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query) {
      throw new AssertionError("nearby entities were not expected");
    }

    @Override
    public ToolResult<NearbyContainerResult> nearbyContainers(NearbyContainerQuery query) {
      throw new AssertionError("container summaries were not expected");
    }

    @Override
    public ToolResult<NearbyContainerSnapshotResult> nearbyContainerSnapshots(
        NearbyContainerQuery query) {
      snapshotReads++;
      lastQuery = query;
      return ToolResult.success(
          new NearbyContainerSnapshotResult(query.radius(), query.limit(), truncated, containers));
    }

    @Override
    public ToolResult<BlockInfo> inspectContainer(ContainerInspectionQuery query) {
      throw new AssertionError("container inspection was not expected");
    }
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
}
