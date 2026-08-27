package me.clutchy.thread.minecraft;

import java.util.List;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.VersionCapabilities;

/** Shared capability contract implemented fully by every currently supported Minecraft version. */
public final class MinecraftCapabilities {
  private static final VersionCapabilities FULLY_SUPPORTED =
      VersionCapabilities.fullySupported(
          List.of(
              ToolId.of("minecraft.get_status"),
              ToolId.of("minecraft.get_game_info"),
              ToolId.of("minecraft.get_client_options"),
              ToolId.of("minecraft.get_player"),
              ToolId.of("minecraft.get_world_info"),
              ToolId.of("minecraft.get_advancements"),
              ToolId.of("minecraft.get_advancement"),
              ToolId.of("minecraft.get_inventory"),
              ToolId.of("minecraft.get_equipment"),
              ToolId.of("minecraft.get_target_block"),
              ToolId.of("minecraft.get_target_entity"),
              ToolId.of("minecraft.get_nearby_containers"),
              ToolId.of("minecraft.inspect_container"),
              ToolId.of("minecraft.get_nearby_entities"),
              ToolId.of("minecraft.get_recipe"),
              ToolId.of("minecraft.can_craft"),
              ToolId.of("minecraft.get_missing_ingredients"),
              ToolId.of("minecraft.get_crafting_plan"),
              ToolId.of("minecraft.find_item"),
              ToolId.of("minecraft.search_items"),
              ToolId.of("minecraft.get_capabilities")));

  private MinecraftCapabilities() {}

  /** Returns the exact twenty-one-tool support map shared by the current version lanes. */
  public static VersionCapabilities fullySupported() {
    return FULLY_SUPPORTED;
  }
}
