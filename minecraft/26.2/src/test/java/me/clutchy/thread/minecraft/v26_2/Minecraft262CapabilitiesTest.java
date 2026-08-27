package me.clutchy.thread.minecraft.v26_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import me.clutchy.thread.core.tool.ToolSupport;
import org.junit.jupiter.api.Test;

class Minecraft262CapabilitiesTest {
  private static final Set<String> EXPECTED_TOOLS =
      Set.of(
          "minecraft.get_status",
          "minecraft.get_game_info",
          "minecraft.get_client_options",
          "minecraft.get_player",
          "minecraft.get_world_info",
          "minecraft.get_advancements",
          "minecraft.get_advancement",
          "minecraft.get_inventory",
          "minecraft.get_equipment",
          "minecraft.get_target_block",
          "minecraft.get_target_entity",
          "minecraft.get_nearby_containers",
          "minecraft.inspect_container",
          "minecraft.get_nearby_entities",
          "minecraft.get_recipe",
          "minecraft.can_craft",
          "minecraft.get_missing_ingredients",
          "minecraft.get_crafting_plan",
          "minecraft.find_item",
          "minecraft.search_items",
          "minecraft.get_capabilities");

  @Test
  void declaresTheCompleteCurrentCatalogAsFullySupported() {
    var capabilities = Minecraft262Capabilities.capabilities();

    assertEquals(21, capabilities.tools().size());
    assertEquals(
        EXPECTED_TOOLS,
        capabilities.tools().keySet().stream()
            .map(Object::toString)
            .collect(Collectors.toUnmodifiableSet()));
    assertTrue(
        capabilities.tools().values().stream()
            .allMatch(support -> support.level() == ToolSupport.Level.FULLY_SUPPORTED));
  }
}
