package me.clutchy.thread.minecraft.v1_21_11;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;
import me.clutchy.thread.core.tool.ToolSupport;
import org.junit.jupiter.api.Test;

class Minecraft12111CapabilitiesTest {
  @Test
  void declaresTheExactFullySupportedCatalog() {
    var capabilities = Minecraft12111Capabilities.capabilities();

    assertEquals(21, capabilities.tools().size());
    assertEquals(
        Set.of(
            "minecraft.can_craft",
            "minecraft.find_item",
            "minecraft.get_advancement",
            "minecraft.get_advancements",
            "minecraft.get_capabilities",
            "minecraft.get_client_options",
            "minecraft.get_crafting_plan",
            "minecraft.get_equipment",
            "minecraft.get_game_info",
            "minecraft.get_inventory",
            "minecraft.get_missing_ingredients",
            "minecraft.get_nearby_containers",
            "minecraft.get_nearby_entities",
            "minecraft.get_player",
            "minecraft.get_recipe",
            "minecraft.get_status",
            "minecraft.get_target_block",
            "minecraft.get_target_entity",
            "minecraft.get_world_info",
            "minecraft.inspect_container",
            "minecraft.search_items"),
        capabilities.tools().keySet().stream()
            .map(Object::toString)
            .collect(Collectors.toUnmodifiableSet()));
    capabilities
        .tools()
        .values()
        .forEach(support -> assertEquals(ToolSupport.Level.FULLY_SUPPORTED, support.level()));
  }
}
