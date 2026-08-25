package me.clutchy.thread.platform.fabric.integration.jei;

import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Packaged-test JEI plugin that contributes recipes absent from Minecraft's recipe manager. */
public final class JeiTestPlugin implements IModPlugin {
  private static final Identifier PLUGIN_ID =
      Identifier.fromNamespaceAndPath("thread", "jei_gametest");
  private static final IRecipeType<TestRecipe> TYPE =
      IRecipeType.create("thread", "jei_gametest", TestRecipe.class);
  private static final List<TestRecipe> RECIPES =
      List.of(
          new TestRecipe(
              Identifier.fromNamespaceAndPath("thread", "jei_barrier_from_earth"),
              List.of(new TestInput(Items.DIRT, 1), new TestInput(Items.STONE, 1)),
              Items.BARRIER),
          new TestRecipe(
              Identifier.fromNamespaceAndPath("thread", "jei_barrier_from_gravel"),
              List.of(new TestInput(Items.GRAVEL, 2)),
              Items.BARRIER),
          new TestRecipe(
              Identifier.fromNamespaceAndPath("thread", "jei_structure_void_from_barrier"),
              List.of(new TestInput(Items.BARRIER, 1)),
              Items.STRUCTURE_VOID));

  @Override
  public Identifier getPluginUid() {
    return PLUGIN_ID;
  }

  @Override
  public void registerCategories(IRecipeCategoryRegistration registration) {
    registration.addRecipeCategories(new TestRecipeCategory());
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    registration.addRecipes(TYPE, RECIPES);
  }

  /** Minimal custom JEI recipe used only by the isolated packaged test mod. */
  public record TestRecipe(Identifier id, List<TestInput> inputs, Item output) {}

  /** One test recipe input with an explicit quantity. */
  public record TestInput(Item item, int count) {
    ItemStack stack() {
      return new ItemStack(item, count);
    }
  }

  private static final class TestRecipeCategory implements IRecipeCategory<TestRecipe> {
    @Override
    public IRecipeType<TestRecipe> getRecipeType() {
      return TYPE;
    }

    @Override
    public Component getTitle() {
      return Component.literal("Thread JEI Test Recipes");
    }

    @Override
    public int getWidth() {
      return 72;
    }

    @Override
    public int getHeight() {
      return 18;
    }

    @Override
    public IDrawable getIcon() {
      return null;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TestRecipe recipe, IFocusGroup focuses) {
      builder
          .addInputSlot(1, 1)
          .addItemStacks(recipe.inputs().stream().map(TestInput::stack).toList());
      builder.addOutputSlot(55, 1).add(recipe.output());
    }

    @Override
    public Identifier getIdentifier(TestRecipe recipe) {
      return recipe.id();
    }
  }
}
