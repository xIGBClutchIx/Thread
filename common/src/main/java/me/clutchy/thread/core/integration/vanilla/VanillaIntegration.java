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
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.recipe.RecipeLookupQuery;
import me.clutchy.thread.core.model.recipe.RecipeLookupResult;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.ContainerInspectionQuery;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.provider.AdvancementProvider;
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
import me.clutchy.thread.core.tool.ToolCapabilities;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.ToolResult;

/**
 * Built-in integration that installs Thread's complete transport-neutral vanilla V1 tool catalog.
 */
public final class VanillaIntegration implements ThreadIntegration {
  private static final IntegrationId ID = IntegrationId.of("vanilla");
  private static final String VERSION = "1";

  private final GameProvider game;
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
      AdvancementProvider advancements,
      PlayerProvider player,
      WorldProvider world,
      RecipeProvider recipes,
      NearbyContainerQuery craftingNearbyBounds,
      Predicate<ToolId> enabledTools,
      Supplier<CapabilitiesSnapshot> capabilities) {
    this.game = Objects.requireNonNull(game, "game");
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
    register(context, getPlayer());
    register(context, getAdvancements());
    register(context, getAdvancement());
    register(context, getInventory());
    register(context, getEquipment());
    register(context, getTargetBlock());
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
        "Reports the current Minecraft session state and whether live gameplay tools are usable. "
            + "Use this as a cheap preflight from menus, loading screens, single-player, or "
            + "unsupported multiplayer sessions.",
        emptyInputCodec(),
        JsonCodec.of(SessionStatus.class, VanillaToolSchemas.SESSION_STATUS),
        ToolCapabilities.alwaysAvailable(),
        ignored -> ToolResult.success(game.sessionStatus()));
  }

  private GameTool<EmptyInput, GameInfo> getGameInfo() {
    return tool(
        "minecraft.get_game_info",
        "Returns the running Minecraft, mod loader, and Thread versions. Use this when the "
            + "answer depends on the actual installed runtime; it remains available from menus.",
        emptyInputCodec(),
        JsonCodec.of(GameInfo.class, VanillaToolSchemas.GAME_INFO),
        ToolCapabilities.alwaysAvailable(),
        ignored -> ToolResult.success(game.gameInfo()));
  }

  private GameTool<EmptyInput, PlayerStatus> getPlayer() {
    return tool(
        "minecraft.get_player",
        "Returns the local player's live health, hunger, experience, position, dimension, and "
            + "game mode. Use this only when the answer depends on current supported single-player "
            + "state.",
        emptyInputCodec(),
        JsonCodec.of(PlayerStatus.class, VanillaToolSchemas.PLAYER_STATUS),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> player.status());
  }

  private GameTool<EmptyInput, InventorySnapshot> getInventory() {
    return tool(
        "minecraft.get_inventory",
        "Returns the local player's non-empty main-inventory slots with canonical IDs, names, "
            + "counts, durability, enchantments, and selected safe components. Equipment is "
            + "excluded. Use this when the answer depends on what the player possesses.",
        emptyInputCodec(),
        JsonCodec.of(InventorySnapshot.class, VanillaToolSchemas.INVENTORY),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> player.inventory());
  }

  private GameTool<AdvancementListQuery, AdvancementListResult> getAdvancements() {
    return tool(
        "minecraft.get_advancements",
        "Returns a bounded deterministic list of vanilla advancements currently visible or known "
            + "to the local player, with live completion summaries. Filter by ALL, COMPLETED, or "
            + "INCOMPLETE and optionally search canonical IDs or display text. Results report both "
            + "query truncation and an incomplete provider snapshot. Use this for progression "
            + "overviews without changing advancement state.",
        JsonCodec.of(AdvancementListQuery.class, VanillaToolSchemas.ADVANCEMENT_LIST_QUERY),
        JsonCodec.of(AdvancementListResult.class, VanillaToolSchemas.ADVANCEMENT_LIST_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        advancements::list);
  }

  private GameTool<AdvancementLookupQuery, AdvancementInfo> getAdvancement() {
    return tool(
        "minecraft.get_advancement",
        "Returns detailed live vanilla progress for one exact advancement ID already known to the "
            + "local player, including criteria state and timestamps, parent/tab context, display "
            + "type, hidden status, and completion. Unknown or undisclosed advancements return "
            + "NOT_FOUND. Use this for exact progress details; no progress or rewards are changed.",
        JsonCodec.of(AdvancementLookupQuery.class, VanillaToolSchemas.ADVANCEMENT_LOOKUP_QUERY),
        JsonCodec.of(AdvancementInfo.class, VanillaToolSchemas.ADVANCEMENT_INFO),
        ToolCapabilities.supportedSingleplayer(),
        advancements::get);
  }

  private GameTool<EmptyInput, EquipmentSnapshot> getEquipment() {
    return tool(
        "minecraft.get_equipment",
        "Returns the local player's live main hand, off hand, armor, and empty equipment positions "
            + "with the same rich item context as inventory. Use this to answer what is currently "
            + "held or worn.",
        emptyInputCodec(),
        JsonCodec.of(EquipmentSnapshot.class, VanillaToolSchemas.EQUIPMENT),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> player.equipment());
  }

  private GameTool<EmptyInput, BlockInfo> getTargetBlock() {
    return tool(
        "minecraft.get_target_block",
        "Returns the block currently under the player's normal camera targeting ray, including "
            + "canonical ID, name, position, state properties, distance, and safe structured "
            + "block-entity data. Use this to identify or inspect what the player is looking at; "
            + "no block is a structured NOT_FOUND result.",
        emptyInputCodec(),
        JsonCodec.of(BlockInfo.class, VanillaToolSchemas.TARGET_BLOCK),
        ToolCapabilities.supportedSingleplayer(),
        ignored -> targetBlock());
  }

  private GameTool<NearbyEntityQuery, NearbyEntityResult> getNearbyEntities() {
    return tool(
        "minecraft.get_nearby_entities",
        "Returns a bounded snapshot of already-loaded entities around the local player, sorted by "
            + "distance, type, and position with names, health, and reliable behavior labels. Use "
            + "this for nearby-entity questions; server-side radius and result limits win and no "
            + "chunks are loaded.",
        JsonCodec.of(NearbyEntityQuery.class, VanillaToolSchemas.NEARBY_ENTITY_QUERY),
        JsonCodec.of(NearbyEntityResult.class, VanillaToolSchemas.NEARBY_ENTITY_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        world::nearbyEntities);
  }

  private GameTool<NearbyContainerQuery, NearbyContainerResult> getNearbyContainers() {
    return tool(
        "minecraft.get_nearby_containers",
        "Returns compact distance-ordered summaries of container block entities in already-loaded "
            + "chunks near the local player. Use this to locate nearby storage or machines before "
            + "inspecting one position; results include occupancy and at most four representative "
            + "slots, never full inventories.",
        JsonCodec.of(NearbyContainerQuery.class, VanillaToolSchemas.NEARBY_CONTAINER_QUERY),
        JsonCodec.of(NearbyContainerResult.class, VanillaToolSchemas.NEARBY_CONTAINER_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        world::nearbyContainers);
  }

  private GameTool<ContainerInspectionQuery, BlockInfo> inspectContainer() {
    return tool(
        "minecraft.inspect_container",
        "Returns the full safe visible inventory and selected machine state for one nearby loaded "
            + "container position. Use this after locating a container; the position must remain "
            + "loaded and within the server-side range, unresolved loot is not opened, and no "
            + "items or world state are changed.",
        JsonCodec.of(ContainerInspectionQuery.class, VanillaToolSchemas.CONTAINER_INSPECTION_QUERY),
        JsonCodec.of(BlockInfo.class, VanillaToolSchemas.CONTAINER_INSPECTION),
        ToolCapabilities.supportedSingleplayer(),
        world::inspectContainer);
  }

  private GameTool<RecipeLookupQuery, RecipeLookupResult> getRecipe() {
    return tool(
        "minecraft.get_recipe",
        "Returns live recipes from the running integrated server that produce one canonical item "
            + "registry ID, preserving item and tag alternatives. Use this for crafting questions "
            + "instead of relying on generic recipe knowledge.",
        JsonCodec.of(RecipeLookupQuery.class, VanillaToolSchemas.RECIPE_LOOKUP_QUERY),
        JsonCodec.of(RecipeLookupResult.class, VanillaToolSchemas.RECIPE_LOOKUP_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        this::lookupRecipe);
  }

  private GameTool<CraftingQuery, CraftingResult> canCraft() {
    return tool(
        "minecraft.can_craft",
        "Determines whether eligible live items can satisfy at least one recipe for a canonical "
            + "item ID. Omit scope or use PLAYER_ONLY for the main inventory; explicitly use "
            + "PLAYER_AND_NEARBY to add bounded loaded containers. Results include deterministic "
            + "source allocations and incomplete-discovery status. Use this for a direct answer; "
            + "no crafting action occurs.",
        JsonCodec.of(CraftingQuery.class, VanillaToolSchemas.CRAFTING_QUERY),
        JsonCodec.of(CraftingResult.class, VanillaToolSchemas.CRAFTING_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        crafting::assess);
  }

  private GameTool<CraftingQuery, CraftingResult> getMissingIngredients() {
    return tool(
        "minecraft.get_missing_ingredients",
        "Returns required, source-allocated, and missing counts for every live recipe variant. "
            + "Scope defaults to PLAYER_ONLY; explicitly use PLAYER_AND_NEARBY when nearby loaded "
            + "containers should count. Bounded or unresolved storage omissions are reported, and "
            + "alternatives never spend one item twice. Use this to explain exact shortages.",
        JsonCodec.of(CraftingQuery.class, VanillaToolSchemas.CRAFTING_QUERY),
        JsonCodec.of(CraftingResult.class, VanillaToolSchemas.CRAFTING_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        crafting::assess);
  }

  private GameTool<CraftingQuery, CraftingPlan> getCraftingPlan() {
    return tool(
        "minecraft.get_crafting_plan",
        "Builds a deterministic bounded recursive plan for one canonical item. Scope defaults to "
            + "PLAYER_ONLY; explicitly use PLAYER_AND_NEARBY to include eligible nearby loaded "
            + "containers. The result reports live source allocations, incomplete discovery, raw "
            + "shortages, cycles, and limits. Use this for step-by-step guidance without crafting "
            + "or moving items.",
        JsonCodec.of(CraftingQuery.class, VanillaToolSchemas.CRAFTING_QUERY),
        JsonCodec.of(CraftingPlan.class, VanillaToolSchemas.CRAFTING_PLAN),
        ToolCapabilities.supportedSingleplayer(),
        craftingPlanner::plan);
  }

  private GameTool<ItemSearchQuery, ItemSearchResult> searchItems() {
    return tool(
        "minecraft.search_items",
        "Searches the running game's item registry by canonical ID and display-name terms, with a "
            + "required result limit and accurate truncation metadata. Use this to resolve friendly "
            + "item names before requesting a recipe.",
        JsonCodec.of(ItemSearchQuery.class, VanillaToolSchemas.ITEM_SEARCH_QUERY),
        JsonCodec.of(ItemSearchResult.class, VanillaToolSchemas.ITEM_SEARCH_RESULT),
        ToolCapabilities.alwaysAvailable(),
        input -> recipes.searchItems(input.query(), input.limit()));
  }

  private GameTool<FindItemQuery, FindItemResult> findItem() {
    return tool(
        "minecraft.find_item",
        "Finds matching live items across the player's main inventory, offhand and armor, plus "
            + "bounded nearby loaded containers. Results aggregate counts by item and source with "
            + "deterministic slots, container positions, and distances; unopened loot is skipped "
            + "and crafting includes nearby storage only when its scope explicitly requests it. "
            + "Use this to locate items without moving them.",
        JsonCodec.of(FindItemQuery.class, VanillaToolSchemas.FIND_ITEM_QUERY),
        JsonCodec.of(FindItemResult.class, VanillaToolSchemas.FIND_ITEM_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        itemFinder::find);
  }

  private GameTool<EmptyInput, CapabilitiesSnapshot> getCapabilities() {
    return tool(
        "minecraft.get_capabilities",
        "Returns Thread's actual registered read-only tools and active game integrations. Use this "
            + "for feature discovery instead of assuming that a tool or integration is installed.",
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
        id, description, inputCodec, outputCodec, capabilities, operation);
  }

  private static <I, O> ToolResult<O> map(
      ToolResult<I> result, Function<? super I, ? extends O> mapper) {
    if (!result.successful()) {
      return ToolResult.failure(Objects.requireNonNull(result.error()));
    }
    return ToolResult.success(mapper.apply(Objects.requireNonNull(result.value())));
  }
}
