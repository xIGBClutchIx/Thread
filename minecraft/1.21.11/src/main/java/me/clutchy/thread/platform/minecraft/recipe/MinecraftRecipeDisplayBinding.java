package me.clutchy.thread.platform.minecraft.recipe;

import java.util.TreeSet;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/** Traverses the recipe slot-display variants available in Minecraft 1.21.11. */
final class MinecraftRecipeDisplayBinding {
  private MinecraftRecipeDisplayBinding() {}

  static void collectTagIds(SlotDisplay display, TreeSet<String> tagIds) {
    if (display instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
      tagIds.add(tagDisplay.tag().location().toString());
    } else if (display instanceof SlotDisplay.Composite composite) {
      composite.contents().forEach(child -> collectTagIds(child, tagIds));
    } else if (display instanceof SlotDisplay.WithRemainder withRemainder) {
      collectTagIds(withRemainder.input(), tagIds);
    }
  }
}
