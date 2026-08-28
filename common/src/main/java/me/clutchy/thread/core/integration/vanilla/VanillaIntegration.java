package me.clutchy.thread.core.integration.vanilla;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.integration.IntegrationContext;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegration;
import me.clutchy.thread.core.model.advancement.AdvancementInfo;
import me.clutchy.thread.core.model.advancement.AdvancementListQuery;
import me.clutchy.thread.core.model.advancement.AdvancementListResult;
import me.clutchy.thread.core.model.advancement.AdvancementLookupQuery;
import me.clutchy.thread.core.model.capability.CapabilitiesSnapshot;
import me.clutchy.thread.core.model.crafting.CraftingPlan;
import me.clutchy.thread.core.model.crafting.CraftingQuery;
import me.clutchy.thread.core.model.crafting.CraftingResult;
import me.clutchy.thread.core.model.game.GameInfo;
import me.clutchy.thread.core.model.game.SessionStatus;
import me.clutchy.thread.core.model.item.ItemSearchQuery;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.item.find.FindItemQuery;
import me.clutchy.thread.core.model.item.find.FindItemResult;
import me.clutchy.thread.core.model.options.ClientOptionsQuery;
import me.clutchy.thread.core.model.options.ClientOptionsSnapshot;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.recipe.RecipeLookupQuery;
import me.clutchy.thread.core.model.recipe.RecipeLookupResult;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.ContainerInspectionQuery;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.model.world.WorldInfo;
import me.clutchy.thread.core.provider.AdvancementProvider;
import me.clutchy.thread.core.provider.ClientOptionsProvider;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.serialization.JsonCodec;
import me.clutchy.thread.core.service.AdvancementService;
import me.clutchy.thread.core.service.CraftingPlanner;
import me.clutchy.thread.core.service.CraftingService;
import me.clutchy.thread.core.service.item.CraftingItemSourceProvider;
import me.clutchy.thread.core.service.item.ItemFinder;
import me.clutchy.thread.core.tool.EmptyInput;
import me.clutchy.thread.core.tool.GameTool;
import me.clutchy.thread.core.tool.ToolAvailability;
import me.clutchy.thread.core.tool.ToolCapabilities;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.ToolResult;

/** Built-in integration that installs Thread's complete transport-neutral vanilla tool catalog. */
public final class VanillaIntegration implements ThreadIntegration {
  private static final IntegrationId ID = IntegrationId.of("vanilla");
  private static final String VERSION = "1";

  private final GameProvider game;
  private final ClientOptionsProvider clientOptions;
  private final PlayerProvider player;
  private final AdvancementService advancements;
  private final WorldProvider world;
  private final RecipeProvider recipes;
  private final CraftingService crafting;
  private final CraftingPlanner craftingPlanner;
  private final ItemFinder itemFinder;
  private final Predicate<ToolId> enabledTools;
  private final Supplier<CapabilitiesSnapshot> capabilities;

  /** Creates a vanilla catalog filtered before tools enter discovery or invocation registries. */
  public VanillaIntegration(
      GameProvider game,
      ClientOptionsProvider clientOptions,
      AdvancementProvider advancements,
      PlayerProvider player,
      WorldProvider world,
      RecipeProvider recipes,
      NearbyContainerQuery craftingNearbyBounds,
      Predicate<ToolId> enabledTools,
      Supplier<CapabilitiesSnapshot> capabilities) {
    this.game = Objects.requireNonNull(game, "game");
    this.clientOptions = Objects.requireNonNull(clientOptions, "clientOptions");
    this.advancements =
        new AdvancementService(Objects.requireNonNull(advancements, "advancements"));
    this.player = Objects.requireNonNull(player, "player");
    this.world = Objects.requireNonNull(world, "world");
    this.recipes = Objects.requireNonNull(recipes, "recipes");
    CraftingItemSourceProvider craftingSources =
        CraftingItemSourceProvider.vanilla(player, world, craftingNearbyBounds);
    crafting = new CraftingService(craftingSources, recipes);
    craftingPlanner = new CraftingPlanner(craftingSources, recipes, crafting);
    itemFinder = ItemFinder.vanilla(player, world);
    this.enabledTools = Objects.requireNonNull(enabledTools, "enabledTools");
    this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
  }

  @Override
  public IntegrationId id() {
    return ID;
  }

  @Override
  public String version() {
    return VERSION;
  }

  @Override
  public String description() {
    return "Built-in read-only tools for the running vanilla Minecraft instance.";
  }

  @Override
  public void register(IntegrationContext context) {
    register(context, getStatus());
    register(context, getGameInfo());
    register(context, getClientOptions());
    register(context, getPlayer());
    register(context, getWorldInfo());
    register(context, getAdvancements());
    register(context, getAdvancement());
    register(context, getInventory());
    register(context, getEquipment());
    register(context, getTargetBlock());
    register(context, getTargetEntity());
    register(context, getNearbyContainers());
    register(context, inspectContainer());
    register(context, getNearbyEntities());
    register(context, getRecipe());
    register(context, canCraft());
    register(context, getMissingIngredients());
    register(context, getCraftingPlan());
    register(context, findItem());
    register(context, searchItems());
    register(context, getCapabilities());
  }

  private GameTool<EmptyInput, SessionStatus> getStatus() {
    return tool(
        "minecraft.get_status",
        "Checks the current client session and whether supported single-player gameplay tools can "
            + "run. Use this as the preflight for menu, loading, or multiplayer state; use "
            + "minecraft.get_game_info for installed versions and minecraft.get_world_info for "
            + "loaded-world details.",
        emptyInputCodec(),
        JsonCodec.of(SessionStatus.class, VanillaToolSchemas.SESSION_STATUS),
        ToolCapabilities.alwaysAvailable(),
        ignored -> ToolResult.success(game.sessionStatus()));
  }

  private GameTool<EmptyInput, GameInfo> getGameInfo() {
    return tool(
        "minecraft.get_game_info",
        "Reports the installed Minecraft, mod-loader, and Thread versions. Use this for runtime "
            + "identity; use minecraft.get_status for session availability and "
            + "minecraft.get_world_info for live world context.",
        emptyInputCodec(),
        JsonCodec.of(GameInfo.class, VanillaToolSchemas.GAME_INFO),
        ToolCapabilities.alwaysAvailable(),
        ignored -> ToolResult.success(game.gameInfo()));
  }

  private GameTool<ClientOptionsQuery, ClientOptionsSnapshot> getClientOptions() {
    return tool(
        "minecraft.get_client_options",
        "Reads selected local client settings without inspecting a world, player, or server. Use "
            + "this for general, video, audio, control, accessibility, or chat options; request "
            + "KEYBINDS explicitly for a bounded binding and conflict list. Do not use it for "
            + "gameplay state.",
        JsonCodec.of(ClientOptionsQuery.class, VanillaToolSchemas.CLIENT_OPTIONS_QUERY),
        JsonCodec.of(ClientOptionsSnapshot.class, VanillaToolSchemas.CLIENT_OPTIONS_SNAPSHOT),
        ToolCapabilities.alwaysAvailable(),
        clientOptions::options);
  }

  private GameTool<EmptyInput, PlayerStatus> getPlayer() {
    return tool(
        "minecraft.get_player",
        "Reports the local player's live vitals, effects, movement, position, game mode, and "
            + "compact vehicle or respawn context. Use this for player condition; use "
            + "minecraft.get_inventory for carried items, minecraft.get_equipment for held or "
            + "worn items, and minecraft.get_world_info for the environment.",
        emptyInputCodec(),
        JsonCodec.of(PlayerStatus.class, VanillaToolSchemas.PLAYER_STATUS),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> player.status());
  }

  private GameTool<EmptyInput, WorldInfo> getWorldInfo() {
    return tool(
        "minecraft.get_world_info",
        "Reports the current dimension, biome, spawn distance, difficulty, time, weather, light, "
            + "moon phase, and biome climate around the player. Use this for live environment "
            + "context; use minecraft.get_player for player condition and minecraft.get_game_info "
            + "for installed versions.",
        emptyInputCodec(),
        JsonCodec.of(WorldInfo.class, VanillaToolSchemas.WORLD_INFO),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> world.worldInfo());
  }

  private GameTool<EmptyInput, InventorySnapshot> getInventory() {
    return tool(
        "minecraft.get_inventory",
        "Lists every non-empty slot in the player's 36-slot main inventory. Use this for a full "
            + "player-inventory snapshot; it excludes equipment and nearby storage. Use "
            + "minecraft.get_equipment for held or worn items and minecraft.find_item to locate "
            + "matching items across all supported live sources.",
        emptyInputCodec(),
        JsonCodec.of(InventorySnapshot.class, VanillaToolSchemas.INVENTORY),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> player.inventory());
  }

  private GameTool<AdvancementListQuery, AdvancementListResult> getAdvancements() {
    return tool(
        "minecraft.get_advancements",
        "Lists bounded vanilla advancement summaries currently visible or known to the player, "
            + "with completion filters and optional text search. Use this for progression "
            + "overviews; use minecraft.get_advancement with one exact advancement ID for "
            + "criteria and timestamps.",
        JsonCodec.of(AdvancementListQuery.class, VanillaToolSchemas.ADVANCEMENT_LIST_QUERY),
        JsonCodec.of(AdvancementListResult.class, VanillaToolSchemas.ADVANCEMENT_LIST_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        advancements::list);
  }

  private GameTool<AdvancementLookupQuery, AdvancementInfo> getAdvancement() {
    return tool(
        "minecraft.get_advancement",
        "Reports criteria, timestamps, hierarchy, display metadata, and completion for one exact "
            + "visible or known vanilla advancement ID. Use this for detailed progress after "
            + "minecraft.get_advancements; unknown or undisclosed IDs return NOT_FOUND.",
        JsonCodec.of(AdvancementLookupQuery.class, VanillaToolSchemas.ADVANCEMENT_LOOKUP_QUERY),
        JsonCodec.of(AdvancementInfo.class, VanillaToolSchemas.ADVANCEMENT_INFO),
        ToolCapabilities.supportedSingleplayer(),
        advancements::get);
  }

  private GameTool<EmptyInput, EquipmentSnapshot> getEquipment() {
    return tool(
        "minecraft.get_equipment",
        "Reports the player's main hand, off hand, and armor slots, including empty positions. Use "
            + "this for what is held or worn; use minecraft.get_inventory for the 36-slot main "
            + "inventory or minecraft.find_item to search every supported live item source.",
        emptyInputCodec(),
        JsonCodec.of(EquipmentSnapshot.class, VanillaToolSchemas.EQUIPMENT),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> player.equipment());
  }

  private GameTool<EmptyInput, BlockInfo> getTargetBlock() {
    return tool(
        "minecraft.get_target_block",
        "Inspects the block under the player's current crosshair, including safe block-entity "
            + "data when present. Use this for the current visual target; use "
            + "minecraft.get_nearby_containers to discover container positions or "
            + "minecraft.inspect_container when a nearby position is already known.",
        emptyInputCodec(),
        JsonCodec.of(BlockInfo.class, VanillaToolSchemas.TARGET_BLOCK),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> targetBlock());
  }

  private GameTool<EmptyInput, EntityInfo> getTargetEntity() {
    return tool(
        "minecraft.get_target_entity",
        "Inspects the exact entity under the player's current crosshair with bounded living, "
            + "equipment, effect, tame, and villager details. Use this for one focused visual "
            + "target; use minecraft.get_nearby_entities for a distance-bounded area scan. No "
            + "target returns NOT_FOUND.",
        emptyInputCodec(),
        JsonCodec.of(EntityInfo.class, VanillaToolSchemas.TARGET_ENTITY),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> targetEntity());
  }

  private GameTool<NearbyEntityQuery, NearbyEntityResult> getNearbyEntities() {
    return tool(
        "minecraft.get_nearby_entities",
        "Scans already-loaded entities around the player within explicit radius and result bounds, "
            + "then sorts them deterministically by distance, type, and position. Use this for an "
            + "area overview; use minecraft.get_target_entity for the exact crosshair target. No "
            + "chunks are loaded.",
        JsonCodec.of(NearbyEntityQuery.class, VanillaToolSchemas.NEARBY_ENTITY_QUERY),
        JsonCodec.of(NearbyEntityResult.class, VanillaToolSchemas.NEARBY_ENTITY_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        world::nearbyEntities);
  }

  private GameTool<NearbyContainerQuery, NearbyContainerResult> getNearbyContainers() {
    return tool(
        "minecraft.get_nearby_containers",
        "Locates nearby loaded containers and returns distance-ordered occupancy summaries with at "
            + "most four representative slots. Use this to discover storage or machine positions; "
            + "use minecraft.inspect_container for one full safe visible inventory. No chunks are "
            + "loaded.",
        JsonCodec.of(NearbyContainerQuery.class, VanillaToolSchemas.NEARBY_CONTAINER_QUERY),
        JsonCodec.of(NearbyContainerResult.class, VanillaToolSchemas.NEARBY_CONTAINER_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        world::nearbyContainers);
  }

  private GameTool<ContainerInspectionQuery, BlockInfo> inspectContainer() {
    return tool(
        "minecraft.inspect_container",
        "Inspects the full safe visible inventory and selected machine state at one known nearby "
            + "loaded container position; no crosshair target is required. Use this after "
            + "minecraft.get_nearby_containers, or use minecraft.get_target_block for the block "
            + "currently under the crosshair. Unresolved loot stays closed.",
        JsonCodec.of(ContainerInspectionQuery.class, VanillaToolSchemas.CONTAINER_INSPECTION_QUERY),
        JsonCodec.of(BlockInfo.class, VanillaToolSchemas.CONTAINER_INSPECTION),
        ToolCapabilities.supportedSingleplayer(),
        world::inspectContainer);
  }

  private GameTool<RecipeLookupQuery, RecipeLookupResult> getRecipe() {
    return tool(
        "minecraft.get_recipe",
        "Lists live recipe variants that produce one exact canonical item ID without assessing "
            + "available supplies. Use this for recipe definitions; use minecraft.can_craft for a "
            + "direct current-supply answer, minecraft.get_missing_ingredients for shortages, or "
            + "minecraft.get_crafting_plan for recursive intermediate steps.",
        JsonCodec.of(RecipeLookupQuery.class, VanillaToolSchemas.RECIPE_LOOKUP_QUERY),
        JsonCodec.of(RecipeLookupResult.class, VanillaToolSchemas.RECIPE_LOOKUP_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        this::lookupRecipe);
  }

  private GameTool<CraftingQuery, CraftingResult> canCraft() {
    return tool(
        "minecraft.can_craft",
        "Checks whether current eligible supplies satisfy one execution of any live recipe for an "
            + "exact item ID. Use this for a direct yes/no answer; use "
            + "minecraft.get_missing_ingredients to explain one-step shortages or "
            + "minecraft.get_crafting_plan for recursive intermediates. Scope defaults to "
            + "PLAYER_ONLY; nearby storage is explicit.",
        JsonCodec.of(CraftingQuery.class, VanillaToolSchemas.CRAFTING_QUERY),
        JsonCodec.of(CraftingResult.class, VanillaToolSchemas.CRAFTING_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        crafting::assess);
  }

  private GameTool<CraftingQuery, CraftingResult> getMissingIngredients() {
    return tool(
        "minecraft.get_missing_ingredients",
        "Explains required, allocated, available, and missing counts for one execution of every "
            + "live recipe variant. Use this for exact one-step shortages; use "
            + "minecraft.can_craft for a direct yes/no answer or minecraft.get_crafting_plan for "
            + "recursive intermediates. Scope defaults to PLAYER_ONLY; nearby storage is explicit.",
        JsonCodec.of(CraftingQuery.class, VanillaToolSchemas.CRAFTING_QUERY),
        JsonCodec.of(CraftingResult.class, VanillaToolSchemas.CRAFTING_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        crafting::assess);
  }

  private GameTool<CraftingQuery, CraftingPlan> getCraftingPlan() {
    return tool(
        "minecraft.get_crafting_plan",
        "Builds a bounded dependency-first crafting plan with intermediate steps, raw shortages, "
            + "source allocations, cycles, and limit issues. Use this for step-by-step guidance; "
            + "use minecraft.get_recipe for recipe definitions or the direct crafting tools for "
            + "one recipe execution. Scope defaults to PLAYER_ONLY; nearby storage is explicit.",
        JsonCodec.of(CraftingQuery.class, VanillaToolSchemas.CRAFTING_QUERY),
        JsonCodec.of(CraftingPlan.class, VanillaToolSchemas.CRAFTING_PLAN),
        ToolCapabilities.supportedSingleplayer(),
        craftingPlanner::plan);
  }

  private GameTool<ItemSearchQuery, ItemSearchResult> searchItems() {
    return tool(
        "minecraft.search_items",
        "Searches registered item definitions by exact canonical ID or display-name terms; it does "
            + "not inspect what the player owns. Use this to resolve a friendly item name before "
            + "minecraft.get_recipe, or use minecraft.find_item to locate matching owned and "
            + "nearby-container stacks.",
        JsonCodec.of(ItemSearchQuery.class, VanillaToolSchemas.ITEM_SEARCH_QUERY),
        JsonCodec.of(ItemSearchResult.class, VanillaToolSchemas.ITEM_SEARCH_RESULT),
        ToolCapabilities.alwaysAvailable(),
        input -> recipes.searchItems(input.query(), input.limit()));
  }

  private GameTool<FindItemQuery, FindItemResult> findItem() {
    return tool(
        "minecraft.find_item",
        "Locates matching live stacks across main inventory, offhand, armor, and nearby loaded "
            + "containers, with aggregated counts and source positions. Use this to answer where "
            + "an item is; use minecraft.get_inventory for a complete main-inventory listing or "
            + "minecraft.search_items for registry definitions. This never changes crafting "
            + "scope.",
        JsonCodec.of(FindItemQuery.class, VanillaToolSchemas.FIND_ITEM_QUERY),
        JsonCodec.of(FindItemResult.class, VanillaToolSchemas.FIND_ITEM_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        itemFinder::find);
  }

  private GameTool<EmptyInput, CapabilitiesSnapshot> getCapabilities() {
    return tool(
        "minecraft.get_capabilities",
        "Reports Thread's currently registered read-only tool IDs and active integration metadata. "
            + "Use this to confirm runtime feature presence after tools/list rather than assuming "
            + "an optional integration activated.",
        emptyInputCodec(),
        JsonCodec.of(CapabilitiesSnapshot.class, VanillaToolSchemas.CAPABILITIES),
        ToolCapabilities.alwaysAvailable(),
        ignored -> ToolResult.success(capabilities.get()));
  }

  private ToolResult<BlockInfo> targetBlock() {
    ToolResult<Optional<BlockInfo>> result = player.targetBlock();
    if (!result.successful()) {
      return ToolResult.failure(Objects.requireNonNull(result.error()));
    }
    return Objects.requireNonNull(result.value())
        .map(ToolResult::success)
        .orElseGet(
            () ->
                ToolResult.failure(
                    new ToolError(
                        ToolErrorCode.NOT_FOUND,
                        "The player is not currently targeting a valid block.",
                        true,
                        Map.of())));
  }

  private ToolResult<EntityInfo> targetEntity() {
    ToolResult<Optional<EntityInfo>> result = world.targetEntity();
    if (!result.successful()) {
      return ToolResult.failure(Objects.requireNonNull(result.error()));
    }
    return Objects.requireNonNull(result.value())
        .map(ToolResult::success)
        .orElseGet(
            () ->
                ToolResult.failure(
                    new ToolError(
                        ToolErrorCode.NOT_FOUND,
                        "The player is not currently targeting a valid entity.",
                        true,
                        Map.of())));
  }

  private ToolResult<RecipeLookupResult> lookupRecipe(RecipeLookupQuery input) {
    return map(
        recipes.recipesFor(input.itemId()),
        matches -> new RecipeLookupResult(input.itemId(), matches));
  }

  private static JsonCodec<EmptyInput> emptyInputCodec() {
    return JsonCodec.of(EmptyInput.class, VanillaToolSchemas.EMPTY_INPUT);
  }

  private void register(IntegrationContext context, GameTool<?, ?> tool) {
    if (enabledTools.test(tool.id())) {
      context.registerTool(tool);
    }
  }

  private static <I, O> GameTool<I, O> tool(
      String id,
      String description,
      JsonCodec<I> inputCodec,
      JsonCodec<O> outputCodec,
      ToolCapabilities capabilities,
      Function<I, ToolResult<O>> operation) {
    return new FunctionalGameTool<>(
        id,
        description + sessionAvailabilityDescription(capabilities.availability()),
        inputCodec,
        outputCodec,
        capabilities,
        operation);
  }

  private static String sessionAvailabilityDescription(ToolAvailability availability) {
    return switch (availability) {
      case ALWAYS -> " Available in the main menu, single-player, and multiplayer.";
      case SUPPORTED_SINGLEPLAYER ->
          " Requires a supported single-player world; menu and multiplayer calls return a "
              + "structured availability error.";
    };
  }

  private static <I, O> ToolResult<O> map(
      ToolResult<I> result, Function<? super I, ? extends O> mapper) {
    if (!result.successful()) {
      return ToolResult.failure(Objects.requireNonNull(result.error()));
    }
    return ToolResult.success(mapper.apply(Objects.requireNonNull(result.value())));
  }
}
