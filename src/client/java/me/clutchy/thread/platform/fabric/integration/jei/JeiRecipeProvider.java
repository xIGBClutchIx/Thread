package me.clutchy.thread.platform.fabric.integration.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.integration.extension.IntegrationRecipeProvider;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.model.recipe.RecipeIngredientInfo;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.fabric.game.FabricProviderSupport;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationServices;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Converts bounded, item-only JEI layouts into Thread's stable recipe model. */
final class JeiRecipeProvider implements IntegrationRecipeProvider {
  private static final System.Logger LOGGER = System.getLogger(JeiRecipeProvider.class.getName());
  private static final int MAX_SLOTS_PER_RECIPE = 64;
  private static final int MAX_ALTERNATIVES_PER_SLOT = 512;

  private final FabricIntegrationServices services;
  private final Function<Item, ItemStack> focusStackFactory;

  JeiRecipeProvider(FabricIntegrationServices services) {
    this(services, Item::getDefaultInstance);
  }

  JeiRecipeProvider(
      FabricIntegrationServices services, Function<Item, ItemStack> focusStackFactory) {
    this.services = Objects.requireNonNull(services, "services");
    this.focusStackFactory = Objects.requireNonNull(focusStackFactory, "focusStackFactory");
  }

  @Override
  public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
    return FabricProviderSupport.read(
        services.clientThread(), "jei.recipes", () -> readRecipes(itemId));
  }

  private ToolResult<List<RecipeInfo>> readRecipes(String itemId) {
    Identifier targetId = Identifier.tryParse(itemId);
    if (targetId == null) {
      return failure(ToolErrorCode.INVALID_INPUT, "itemId must be a valid registry ID.");
    }
    Optional<Item> targetItem = BuiltInRegistries.ITEM.getOptional(targetId);
    if (targetItem.isEmpty()) {
      return failure(ToolErrorCode.NOT_FOUND, "The requested item is not registered.");
    }
    Optional<IJeiRuntime> runtime = JeiRuntimeBridge.runtime();
    if (runtime.isEmpty()) {
      return failure(ToolErrorCode.NOT_AVAILABLE, "JEI's recipe runtime is not available yet.");
    }

    try {
      return query(runtime.orElseThrow(), targetItem.orElseThrow(), targetId);
    } catch (RecipeLimitException exception) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.RESULT_LIMIT_EXCEEDED,
              exception.getMessage(),
              false,
              Map.of("limit", Integer.toString(exception.limit()))));
    }
  }

  private ToolResult<List<RecipeInfo>> query(
      IJeiRuntime runtime, Item targetItem, Identifier targetId) {
    IRecipeManager manager = runtime.getRecipeManager();
    IFocus<ItemStack> focus =
        runtime
            .getJeiHelpers()
            .getFocusFactory()
            .createFocus(
                RecipeIngredientRole.OUTPUT,
                VanillaTypes.ITEM_STACK,
                Objects.requireNonNull(focusStackFactory.apply(targetItem), "JEI focus stack"));
    IFocusGroup focusGroup =
        runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of(focus));
    List<IRecipeCategory<?>> categories =
        manager
            .createRecipeCategoryLookup()
            .limitFocus(List.of(focus))
            .get()
            .limit((long) services.limits().maxRecipeDefinitions() + 1)
            .toList();
    if (categories.size() > services.limits().maxRecipeDefinitions()) {
      throw new RecipeLimitException(
          "JEI returned more recipe categories than Thread can inspect safely.",
          services.limits().maxRecipeDefinitions());
    }

    List<RecipeInfo> recipes = new ArrayList<>();
    int[] inspected = {0};
    for (IRecipeCategory<?> category : categories) {
      appendCategory(manager, category, focus, focusGroup, targetId, recipes, inspected);
    }
    recipes.sort(
        Comparator.comparing(RecipeInfo::recipeId)
            .thenComparing(RecipeInfo::type)
            .thenComparing(recipe -> recipe.ingredients().toString()));
    return ToolResult.success(List.copyOf(recipes));
  }

  private <T> void appendCategory(
      IRecipeManager manager,
      IRecipeCategory<T> category,
      IFocus<?> focus,
      IFocusGroup focusGroup,
      Identifier targetId,
      List<RecipeInfo> recipes,
      int[] inspected) {
    int remaining = services.limits().maxRecipeDefinitions() - inspected[0];
    List<T> categoryRecipes =
        manager
            .createRecipeLookup(category.getRecipeType())
            .limitFocus(List.of(focus))
            .get()
            .limit((long) remaining + 1)
            .toList();
    if (categoryRecipes.size() > remaining) {
      throw new RecipeLimitException(
          "JEI returned more recipes than Thread can inspect safely.",
          services.limits().maxRecipeDefinitions());
    }
    for (T recipe : categoryRecipes) {
      inspected[0]++;
      if (inspected[0] > services.limits().maxRecipeDefinitions()) {
        throw new RecipeLimitException(
            "JEI returned more recipes than Thread can inspect safely.",
            services.limits().maxRecipeDefinitions());
      }
      try {
        toRecipeInfo(manager, category, recipe, focusGroup, targetId).ifPresent(recipes::add);
      } catch (RuntimeException exception) {
        // One malformed third-party layout must not hide other representable JEI recipes.
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Skipped one JEI recipe layout ({0})",
            exception.getClass().getName());
      }
      if (recipes.size() > services.limits().maxRecipesPerItem()) {
        throw new RecipeLimitException(
            "JEI returned more matching recipes than Thread can expose safely.",
            services.limits().maxRecipesPerItem());
      }
    }
  }

  private <T> Optional<RecipeInfo> toRecipeInfo(
      IRecipeManager manager,
      IRecipeCategory<T> category,
      T recipe,
      IFocusGroup focusGroup,
      Identifier targetId) {
    Identifier recipeId = category.getIdentifier(recipe);
    if (recipeId == null) {
      return Optional.empty();
    }
    Optional<IRecipeLayoutDrawable<T>> drawable =
        manager.createRecipeLayoutDrawable(category, recipe, focusGroup);
    if (drawable.isEmpty()) {
      return Optional.empty();
    }
    List<IRecipeSlotView> slots = drawable.orElseThrow().getRecipeSlotsView().getSlotViews();
    if (slots.size() > MAX_SLOTS_PER_RECIPE) {
      return Optional.empty();
    }

    Optional<ItemStack> result = resultStack(slots, targetId);
    Optional<List<RecipeIngredientInfo>> ingredients = ingredientGroups(slots);
    if (result.isEmpty() || ingredients.isEmpty() || ingredients.orElseThrow().isEmpty()) {
      return Optional.empty();
    }
    ItemStackInfo resultInfo = services.mapper().itemStack(result.orElseThrow());
    return Optional.of(
        new RecipeInfo(
            recipeId.toString(),
            category.getRecipeType().getUid().toString(),
            resultInfo,
            ingredients.orElseThrow()));
  }

  private static Optional<ItemStack> resultStack(List<IRecipeSlotView> slots, Identifier targetId) {
    List<IRecipeSlotView> outputSlots =
        slots.stream().filter(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT).toList();
    if (outputSlots.size() != 1) {
      return Optional.empty();
    }
    Optional<List<ItemStack>> candidates = itemCandidates(outputSlots.getFirst());
    if (candidates.isEmpty() || candidates.orElseThrow().isEmpty()) {
      return Optional.empty();
    }
    List<ItemStack> matching =
        candidates.orElseThrow().stream()
            .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(targetId))
            .toList();
    if (matching.size() != candidates.orElseThrow().size()) {
      return Optional.empty();
    }
    int count = matching.getFirst().getCount();
    if (matching.stream().anyMatch(stack -> stack.getCount() != count)) {
      return Optional.empty();
    }
    return Optional.of(matching.getFirst().copy());
  }

  private static Optional<List<RecipeIngredientInfo>> ingredientGroups(
      List<IRecipeSlotView> slots) {
    Map<IngredientAlternatives, Integer> groups = new LinkedHashMap<>();
    for (IRecipeSlotView slot : slots) {
      if (slot.getRole() != RecipeIngredientRole.INPUT) {
        continue;
      }
      Optional<List<ItemStack>> candidates = itemCandidates(slot);
      if (candidates.isEmpty()) {
        return Optional.empty();
      }
      if (candidates.orElseThrow().isEmpty()) {
        continue;
      }
      TreeSet<String> itemIds = new TreeSet<>();
      int count = candidates.orElseThrow().getFirst().getCount();
      for (ItemStack stack : candidates.orElseThrow()) {
        if (stack.getCount() != count) {
          return Optional.empty();
        }
        itemIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
      }
      List<String> tagIds =
          slot.getTagKey().map(tag -> List.of(tag.location().toString())).orElseGet(List::of);
      groups.merge(new IngredientAlternatives(List.copyOf(itemIds), tagIds), count, Integer::sum);
    }
    return Optional.of(
        groups.entrySet().stream()
            .map(
                entry ->
                    new RecipeIngredientInfo(
                        entry.getKey().itemIds(), entry.getKey().tagIds(), entry.getValue()))
            .toList());
  }

  private static Optional<List<ItemStack>> itemCandidates(IRecipeSlotView slot) {
    List<ITypedIngredient<?>> displayed =
        slot.getDisplayedIngredients().limit((long) MAX_ALTERNATIVES_PER_SLOT + 1).toList();
    if (displayed.size() > MAX_ALTERNATIVES_PER_SLOT) {
      return Optional.empty();
    }
    List<ItemStack> stacks =
        displayed.stream()
            .map(ingredient -> ingredient.getIngredient(VanillaTypes.ITEM_STACK))
            .flatMap(Optional::stream)
            .filter(stack -> !stack.isEmpty())
            .toList();
    if (stacks.size() != displayed.size()) {
      return Optional.empty();
    }
    return Optional.of(stacks);
  }

  private static <T> ToolResult<T> failure(ToolErrorCode code, String message) {
    return ToolResult.failure(ToolError.of(code, message, true));
  }

  private record IngredientAlternatives(List<String> itemIds, List<String> tagIds) {}

  private static final class RecipeLimitException extends RuntimeException {
    private final int limit;

    RecipeLimitException(String message, int limit) {
      super(message);
      this.limit = limit;
    }

    int limit() {
      return limit;
    }
  }
}
