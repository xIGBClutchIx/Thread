package me.clutchy.thread.platform.fabric.integration.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.fabric.game.FabricProviderLimits;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationServices;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import me.clutchy.thread.platform.fabric.testing.MinecraftTestBootstrap;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeCategoriesLookup;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JeiRecipeProviderTest {
  private static final IRecipeType<TestRecipe> TYPE =
      IRecipeType.create("thread", "test_jei", TestRecipe.class);

  @BeforeAll
  static void initializeMinecraft() {
    MinecraftTestBootstrap.initialize();
  }

  @AfterEach
  void clearRuntime() {
    JeiRuntimeBridge.unavailable();
  }

  @Test
  void convertsAlternativesTagsCountsAndStableRecipeVariants() {
    TagKey<Item> logs =
        TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", "logs"));
    TestRecipe later =
        recipe(
            "thread:z_later",
            slot(RecipeIngredientRole.INPUT, List.of(stack(Items.GRAVEL, 2)), null, false));
    TestRecipe earlier =
        recipe(
            "thread:a_earlier",
            slot(
                RecipeIngredientRole.INPUT,
                List.of(stack(Items.OAK_LOG), stack(Items.BIRCH_LOG)),
                logs,
                false));
    JeiRuntimeBridge.available(runtime(List.of(later, earlier)));

    ToolResult<List<RecipeInfo>> result = provider().recipesFor("minecraft:barrier");

    assertTrue(result.successful());
    assertEquals(
        List.of("thread:a_earlier", "thread:z_later"),
        result.value().stream().map(RecipeInfo::recipeId).toList());
    assertEquals(
        List.of("minecraft:birch_log", "minecraft:oak_log"),
        result.value().getFirst().ingredients().getFirst().itemIds());
    assertEquals(
        List.of("minecraft:logs"), result.value().getFirst().ingredients().getFirst().tagIds());
    assertEquals(2, result.value().get(1).ingredients().getFirst().count());
  }

  @Test
  void skipsRecipesWithNonItemInputsInsteadOfInventingData() {
    TestRecipe unsupported =
        recipe(
            "thread:unsupported",
            slot(RecipeIngredientRole.INPUT, List.of(stack(Items.DIRT)), null, true));
    TestRecipe supported =
        recipe(
            "thread:supported",
            slot(RecipeIngredientRole.INPUT, List.of(stack(Items.STONE)), null, false));
    JeiRuntimeBridge.available(runtime(List.of(unsupported, supported)));

    ToolResult<List<RecipeInfo>> result = provider().recipesFor("minecraft:barrier");

    assertTrue(result.successful());
    assertEquals(
        List.of("thread:supported"), result.value().stream().map(RecipeInfo::recipeId).toList());
  }

  @Test
  void unavailableRuntimeFailsSoftlyForTheCompositeFallback() {
    ToolResult<List<RecipeInfo>> result = provider().recipesFor("minecraft:barrier");

    assertFalse(result.successful());
    assertEquals(ToolErrorCode.NOT_AVAILABLE, result.error().code());
  }

  private static JeiRecipeProvider provider() {
    GameThreadExecutor direct =
        new GameThreadExecutor() {
          @Override
          public <T> T call(Supplier<T> operation) {
            return operation.get();
          }
        };
    return new JeiRecipeProvider(
        new FabricIntegrationServices(
            direct, FabricProviderLimits.defaults(), new FabricDtoMapper()),
        JeiRecipeProviderTest::stack);
  }

  private static TestRecipe recipe(String id, TestSlot... inputs) {
    return new TestRecipe(
        Identifier.parse(id),
        List.of(inputs),
        slot(RecipeIngredientRole.OUTPUT, List.of(stack(Items.BARRIER)), null, false));
  }

  private static TestSlot slot(
      RecipeIngredientRole role, List<ItemStack> stacks, TagKey<?> tag, boolean customIngredient) {
    return new TestSlot(role, stacks, tag, customIngredient);
  }

  private static ItemStack stack(Item item) {
    return stack(item, 1);
  }

  private static ItemStack stack(Item item, int count) {
    ItemStack stack =
        new ItemStack(Holder.direct(item, DataComponents.COMMON_ITEM_COMPONENTS), count);
    stack.set(
        DataComponents.ITEM_NAME,
        Component.literal(BuiltInRegistries.ITEM.getKey(item).toString()));
    return stack;
  }

  @SuppressWarnings("unchecked")
  private static IJeiRuntime runtime(List<TestRecipe> recipes) {
    IFocus<?> focus = proxyMethod(IFocus.class, ignored -> null);
    IFocusGroup focusGroup = proxyMethod(IFocusGroup.class, ignored -> null);
    IFocusFactory focusFactory =
        proxyMethod(
            IFocusFactory.class,
            method -> method.getName().equals("createFocusGroup") ? focusGroup : focus);
    IJeiHelpers helpers =
        proxyMethod(
            IJeiHelpers.class,
            method -> {
              if (method.getName().equals("getFocusFactory")) {
                return focusFactory;
              }
              throw unexpected(method);
            });

    IRecipeCategory<TestRecipe> category =
        proxyInvocation(
            IRecipeCategory.class,
            invocation -> {
              Method method = invocation.method();
              return switch (method.getName()) {
                case "getRecipeType" -> TYPE;
                case "getIdentifier" -> ((TestRecipe) invocation.arguments()[0]).id();
                default -> throw unexpected(method);
              };
            });
    IRecipeCategoriesLookup[] categoriesLookup = new IRecipeCategoriesLookup[1];
    categoriesLookup[0] =
        proxyMethod(
            IRecipeCategoriesLookup.class,
            method ->
                switch (method.getName()) {
                  case "limitFocus" -> categoriesLookup[0];
                  case "get" -> Stream.of(category);
                  default -> throw unexpected(method);
                });
    IRecipeLookup<TestRecipe>[] recipeLookup = new IRecipeLookup[1];
    recipeLookup[0] =
        proxyMethod(
            IRecipeLookup.class,
            method ->
                switch (method.getName()) {
                  case "limitFocus" -> recipeLookup[0];
                  case "get" -> recipes.stream();
                  default -> throw unexpected(method);
                });
    IRecipeManager manager =
        proxyInvocation(
            IRecipeManager.class,
            invocation -> {
              Method method = invocation.method();
              return switch (method.getName()) {
                case "createRecipeCategoryLookup" -> categoriesLookup[0];
                case "createRecipeLookup" -> recipeLookup[0];
                case "createRecipeLayoutDrawable" ->
                    Optional.of(layout((TestRecipe) invocation.arguments()[1]));
                default -> throw unexpected(method);
              };
            });
    return proxyMethod(
        IJeiRuntime.class,
        method ->
            switch (method.getName()) {
              case "getRecipeManager" -> manager;
              case "getJeiHelpers" -> helpers;
              default -> throw unexpected(method);
            });
  }

  @SuppressWarnings("unchecked")
  private static IRecipeLayoutDrawable<TestRecipe> layout(TestRecipe recipe) {
    List<IRecipeSlotView> slots =
        Stream.concat(recipe.inputs().stream(), Stream.of(recipe.output()))
            .map(JeiRecipeProviderTest::slotView)
            .toList();
    IRecipeSlotsView slotsView =
        proxyMethod(
            IRecipeSlotsView.class,
            method -> {
              if (method.getName().equals("getSlotViews")) {
                return slots;
              }
              throw unexpected(method);
            });
    return (IRecipeLayoutDrawable<TestRecipe>)
        proxyMethod(
            IRecipeLayoutDrawable.class,
            method -> {
              if (method.getName().equals("getRecipeSlotsView")) {
                return slotsView;
              }
              throw unexpected(method);
            });
  }

  private static IRecipeSlotView slotView(TestSlot slot) {
    List<ITypedIngredient<?>> ingredients =
        slot.stacks().stream()
            .<ITypedIngredient<?>>map(stack -> typedIngredient(stack, slot.customIngredient()))
            .toList();
    return proxyMethod(
        IRecipeSlotView.class,
        method ->
            switch (method.getName()) {
              case "getRole" -> slot.role();
              case "getAllIngredients" -> ingredients.stream();
              case "getTagKey" -> Optional.ofNullable(slot.tag());
              default -> throw unexpected(method);
            });
  }

  private static ITypedIngredient<?> typedIngredient(ItemStack stack, boolean customIngredient) {
    return proxyInvocation(
        ITypedIngredient.class,
        invocation -> {
          if (invocation.method().getName().equals("getIngredient")) {
            return customIngredient || invocation.arguments()[0] != VanillaTypes.ITEM_STACK
                ? Optional.empty()
                : Optional.of(stack);
          }
          throw unexpected(invocation.method());
        });
  }

  private static IllegalStateException unexpected(Method method) {
    return new IllegalStateException("Unexpected JEI test call: " + method);
  }

  private static <T> T proxyMethod(Class<T> contract, Function<Method, Object> handler) {
    return proxyInvocation(contract, invocation -> handler.apply(invocation.method()));
  }

  private static <T> T proxyInvocation(Class<T> contract, Function<Invocation, Object> handler) {
    Object instance =
        Proxy.newProxyInstance(
            contract.getClassLoader(),
            new Class<?>[] {contract},
            (proxy, method, arguments) -> {
              if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                  case "toString" -> contract.getSimpleName() + " test proxy";
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> proxy == arguments[0];
                  default -> throw unexpected(method);
                };
              }
              return handler.apply(
                  new Invocation(method, arguments == null ? new Object[0] : arguments));
            });
    return contract.cast(instance);
  }

  private record TestRecipe(Identifier id, List<TestSlot> inputs, TestSlot output) {}

  private record TestSlot(
      RecipeIngredientRole role, List<ItemStack> stacks, TagKey<?> tag, boolean customIngredient) {}

  private record Invocation(Method method, Object[] arguments) {}
}
