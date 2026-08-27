package me.clutchy.thread.minecraft.v26_2;

import java.util.List;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.VersionCapabilities;

/** Complete built-in tool support for the Minecraft 26.2 adapter. */
public final class Minecraft262Capabilities {
  private static final List<ToolId> TOOLS =
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
          ToolId.of("minecraft.get_capabilities"));

  private static final VersionCapabilities CAPABILITIES = VersionCapabilities.fullySupported(TOOLS);

  private Minecraft262Capabilities() {}

  /** Returns the exact twenty-one-tool support map for Minecraft 26.2. */
  public static VersionCapabilities capabilities() {
    return CAPABILITIES;
  }
}
