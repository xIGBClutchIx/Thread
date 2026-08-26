package me.clutchy.thread.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.crafting.CraftingQuery;
import me.clutchy.thread.core.model.crafting.CraftingResult;
import me.clutchy.thread.core.model.crafting.CraftingScope;
import me.clutchy.thread.core.model.crafting.IngredientAvailability;
import me.clutchy.thread.core.model.crafting.RecipeCraftability;
import me.clutchy.thread.core.model.item.ItemInfo;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySlotInfo;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.model.recipe.RecipeIngredientInfo;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.service.item.CraftingItemSourceProvider;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class CraftingServiceTest {
  private static final CraftingQuery PICKAXE =
      new CraftingQuery("minecraft:diamond_pickaxe", CraftingScope.PLAYER_ONLY);

  @Test
  void marksACompleteShapedRecipeCraftable() {
    CraftingResult result =
        assess(
            inventory(
                stack("minecraft:diamond", 1),
                stack("minecraft:stick", 2),
                stack("minecraft:diamond", 2)),
            recipe(
                "minecraft:diamond_pickaxe",
                "minecraft:crafting_shaped",
                ingredient("minecraft:diamond", 3),
                ingredient("minecraft:stick", 2)));

    assertTrue(result.craftable());
    assertEquals(1, result.recipes().size());
    assertTrue(result.recipes().getFirst().craftable());
    assertTrue(
        result.recipes().getFirst().ingredients().stream()
            .allMatch(ingredient -> ingredient.missing() == 0));
  }

  @Test
  void reportsMissingCountsFromAnEmptyInventory() {
    CraftingResult result =
        assess(
            inventory(),
            recipe(
                "minecraft:diamond_pickaxe",
                "minecraft:crafting_shaped",
                ingredient("minecraft:diamond", 3),
                ingredient("minecraft:stick", 2)));

    assertFalse(result.craftable());
    assertEquals(3, availability(result, "minecraft:diamond").missing());
    assertEquals(0, availability(result, "minecraft:diamond").available());
    assertEquals(2, availability(result, "minecraft:stick").missing());
    assertTrue(availability(result, "minecraft:stick").allocations().isEmpty());
  }

  @Test
  void assessesEveryShapedAndShapelessVariantIndependently() {
    RecipeInfo shaped =
        recipe(
            "thread:pickaxe_variant",
            "minecraft:crafting_shaped",
            ingredient("minecraft:diamond", 3),
            ingredient("minecraft:stick", 2));
    RecipeInfo shapeless =
        recipe(
            "thread:pickaxe_variant",
            "minecraft:crafting_shapeless",
            ingredient("minecraft:emerald", 1),
            ingredient("minecraft:stick", 1));

    CraftingResult result =
        assess(
            inventory(stack("minecraft:emerald", 1), stack("minecraft:stick", 1)),
            shapeless,
            shaped);

    assertTrue(result.craftable());
    assertEquals(
        List.of(1, 2), result.recipes().stream().map(RecipeCraftability::variant).toList());
    assertEquals(
        List.of("minecraft:crafting_shaped", "minecraft:crafting_shapeless"),
        result.recipes().stream().map(RecipeCraftability::type).toList());
    assertFalse(result.recipes().get(0).craftable());
    assertTrue(result.recipes().get(1).craftable());
  }

  @Test
  void allocatesAlternativesAndDuplicateRequirementsWithoutDoubleCounting() {
    RecipeIngredientInfo flexible =
        new RecipeIngredientInfo(
            List.of("minecraft:birch_planks", "minecraft:oak_planks"),
            List.of("minecraft:planks"),
            1);
    RecipeInfo recipe =
        recipe(
            "thread:overlapping_planks",
            "minecraft:crafting_shapeless",
            new RecipeIngredientInfo(List.of("minecraft:oak_planks"), List.of(), 1),
            flexible,
            flexible);

    CraftingResult result =
        assess(
            inventory(stack("minecraft:oak_planks", 1), stack("minecraft:birch_planks", 1)),
            recipe);

    RecipeCraftability assessment = result.recipes().getFirst();
    IngredientAvailability constrained = assessment.ingredients().get(0);
    IngredientAvailability alternatives = assessment.ingredients().get(1);
    assertFalse(assessment.craftable());
    assertEquals(List.of("minecraft:oak_planks"), constrained.itemIds());
    assertEquals(1, constrained.available());
    assertEquals(0, constrained.missing());
    assertEquals(List.of("minecraft:planks"), alternatives.tagIds());
    assertEquals(2, alternatives.required());
    assertEquals(1, alternatives.available());
    assertEquals(1, alternatives.missing());
    assertEquals("minecraft:birch_planks", alternatives.allocations().getFirst().itemId());
  }

  @Test
  void returnsAnEmptyDeterministicResultWhenNoRecipeMatches() {
    FakePlayerProvider player = new FakePlayerProvider(inventory(stack("minecraft:diamond", 64)));
    CraftingService service =
        new CraftingService(
            CraftingItemSourceProvider.playerOnly(player), new FakeRecipeProvider(List.of()));

    ToolResult<CraftingResult> result = service.assess(PICKAXE);

    assertTrue(result.successful());
    assertFalse(result.value().craftable());
    assertTrue(result.value().recipes().isEmpty());
    assertEquals(1, player.inventoryReads);
  }

  @Test
  void preservesDeterministicVariantAndAllocationOrdering() {
    RecipeInfo shaped =
        recipe(
            "thread:duplicate_id", "minecraft:crafting_shaped", ingredient("minecraft:stick", 1));
    RecipeInfo shapeless =
        recipe(
            "thread:duplicate_id",
            "minecraft:crafting_shapeless",
            new RecipeIngredientInfo(
                List.of("minecraft:oak_planks", "minecraft:birch_planks"),
                List.of("minecraft:planks"),
                2));
    InventorySnapshot inventory =
        inventory(
            stack("minecraft:oak_planks", 1),
            stack("minecraft:birch_planks", 1),
            stack("minecraft:stick", 1));

    CraftingResult first = assess(inventory, shapeless, shaped);
    CraftingResult second = assess(inventory, shaped, shapeless);

    assertEquals(first, second);
    assertEquals(
        List.of("minecraft:birch_planks", "minecraft:oak_planks"),
        first.recipes().get(1).ingredients().getFirst().allocations().stream()
            .map(allocation -> allocation.itemId())
            .toList());
  }

  @Test
  void propagatesNoWorldAndMultiplayerFailures() {
    assertEquals(
        ToolErrorCode.WORLD_NOT_AVAILABLE,
        new CraftingService(
                CraftingItemSourceProvider.playerOnly(new FakePlayerProvider(inventory())),
                new FailingRecipeProvider(ToolErrorCode.WORLD_NOT_AVAILABLE))
            .assess(PICKAXE)
            .error()
            .code());

    RecipeProvider availableRecipes =
        new FakeRecipeProvider(
            List.of(
                recipe(
                    "minecraft:diamond_pickaxe",
                    "minecraft:crafting_shaped",
                    ingredient("minecraft:diamond", 3))));
    assertEquals(
        ToolErrorCode.UNSUPPORTED,
        new CraftingService(
                CraftingItemSourceProvider.playerOnly(
                    new FailingPlayerProvider(ToolErrorCode.UNSUPPORTED)),
                availableRecipes)
            .assess(PICKAXE)
            .error()
            .code());
  }

  private static CraftingResult assess(InventorySnapshot inventory, RecipeInfo... recipes) {
    ToolResult<CraftingResult> result =
        new CraftingService(
                CraftingItemSourceProvider.playerOnly(new FakePlayerProvider(inventory)),
                new FakeRecipeProvider(List.of(recipes)))
            .assess(PICKAXE);
    assertTrue(result.successful(), () -> String.valueOf(result.error()));
    return result.value();
  }

  private static IngredientAvailability availability(CraftingResult result, String itemId) {
    return result.recipes().getFirst().ingredients().stream()
        .filter(ingredient -> ingredient.itemIds().contains(itemId))
        .findFirst()
        .orElseThrow();
  }

  private static RecipeIngredientInfo ingredient(String itemId, int count) {
    return new RecipeIngredientInfo(List.of(itemId), List.of(), count);
  }

  private static RecipeInfo recipe(String id, String type, RecipeIngredientInfo... ingredients) {
    return new RecipeInfo(id, type, item("minecraft:diamond_pickaxe", 1), List.of(ingredients));
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

  private static final class FakePlayerProvider implements PlayerProvider {
    private final InventorySnapshot inventory;
    private int inventoryReads;

    private FakePlayerProvider(InventorySnapshot inventory) {
      this.inventory = inventory;
    }

    @Override
    public ToolResult<PlayerStatus> status() {
      throw new AssertionError("status should not be read by crafting");
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
      inventoryReads++;
      return ToolResult.success(inventory);
    }

    @Override
    public ToolResult<EquipmentSnapshot> equipment() {
      throw new AssertionError("equipment should not be read by crafting");
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      throw new AssertionError("target block should not be read by crafting");
    }
  }

  private static final class FailingPlayerProvider implements PlayerProvider {
    private final ToolError error;

    private FailingPlayerProvider(ToolErrorCode code) {
      error = ToolError.of(code, "Unavailable for test.", true);
    }

    @Override
    public ToolResult<PlayerStatus> status() {
      throw new AssertionError("status should not be read by crafting");
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<EquipmentSnapshot> equipment() {
      throw new AssertionError("equipment should not be read by crafting");
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      throw new AssertionError("target block should not be read by crafting");
    }
  }

  private record FakeRecipeProvider(List<RecipeInfo> recipes) implements RecipeProvider {
    @Override
    public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
      return ToolResult.success(recipes);
    }

    @Override
    public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
      return ToolResult.success(new ItemSearchResult(query, limit, false, List.<ItemInfo>of()));
    }
  }

  private static final class FailingRecipeProvider implements RecipeProvider {
    private final ToolError error;

    private FailingRecipeProvider(ToolErrorCode code) {
      error = ToolError.of(code, "Unavailable for test.", true);
    }

    @Override
    public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
      throw new AssertionError("item search should not be used by crafting");
    }
  }
}
