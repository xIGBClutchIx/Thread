package me.clutchy.thread.core.integration.vanilla;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.integration.GameIntegration;
import me.clutchy.thread.core.integration.IntegrationContext;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.model.capability.CapabilitiesSnapshot;
import me.clutchy.thread.core.model.capability.IntegrationCapability;
import me.clutchy.thread.core.model.crafting.CraftingResult;
import me.clutchy.thread.core.model.game.GameInfo;
import me.clutchy.thread.core.model.game.SessionStatus;
import me.clutchy.thread.core.model.item.ItemSearchQuery;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.recipe.RecipeLookupQuery;
import me.clutchy.thread.core.model.recipe.RecipeLookupResult;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.serialization.JsonCodec;
import me.clutchy.thread.core.service.CraftingService;
import me.clutchy.thread.core.tool.EmptyInput;
import me.clutchy.thread.core.tool.GameTool;
import me.clutchy.thread.core.tool.ToolCapabilities;
import me.clutchy.thread.core.tool.ToolDescriptor;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.ToolResult;

/**
 * Built-in integration that installs Thread's complete transport-neutral vanilla V1 tool catalog.
 */
public final class VanillaIntegration implements GameIntegration {
  private static final IntegrationId ID = IntegrationId.of("vanilla");
  private static final String VERSION = "1";

  private final GameProvider game;
  private final PlayerProvider player;
  private final WorldProvider world;
  private final RecipeProvider recipes;
  private final CraftingService crafting;
  private final Predicate<ToolId> enabledTools;

  /** Creates the vanilla catalog over explicit loader-neutral providers. */
  public VanillaIntegration(
      GameProvider game, PlayerProvider player, WorldProvider world, RecipeProvider recipes) {
    this(game, player, world, recipes, ignored -> true);
  }

  /** Creates a vanilla catalog filtered before tools enter discovery or invocation registries. */
  public VanillaIntegration(
      GameProvider game,
      PlayerProvider player,
      WorldProvider world,
      RecipeProvider recipes,
      Predicate<ToolId> enabledTools) {
    this.game = Objects.requireNonNull(game, "game");
    this.player = Objects.requireNonNull(player, "player");
    this.world = Objects.requireNonNull(world, "world");
    this.recipes = Objects.requireNonNull(recipes, "recipes");
    crafting = new CraftingService(player, recipes);
    this.enabledTools = Objects.requireNonNull(enabledTools, "enabledTools");
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
    register(context, getInventory());
    register(context, getEquipment());
    register(context, getTargetBlock());
    register(context, getNearbyEntities());
    register(context, getRecipe());
    register(context, canCraft());
    register(context, getMissingIngredients());
    register(context, searchItems());
    register(context, getCapabilities(context));
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
        "Returns the running Minecraft, Fabric Loader, and Thread versions. Use this when the "
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

  private GameTool<RecipeLookupQuery, CraftingResult> canCraft() {
    return tool(
        "minecraft.can_craft",
        "Determines whether the current main inventory can satisfy at least one live recipe for "
            + "a canonical item ID. Use this for a direct yes-or-no answer backed by deterministic, "
            + "non-overlapping allocations for each variant; no crafting action is performed.",
        JsonCodec.of(RecipeLookupQuery.class, VanillaToolSchemas.RECIPE_LOOKUP_QUERY),
        JsonCodec.of(CraftingResult.class, VanillaToolSchemas.CRAFTING_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        crafting::assess);
  }

  private GameTool<RecipeLookupQuery, CraftingResult> getMissingIngredients() {
    return tool(
        "minecraft.get_missing_ingredients",
        "Returns required, allocated, and missing item counts for every live recipe variant that "
            + "produces a canonical item ID. Use this to explain shortages from missing counts "
            + "above zero; alternatives never double-count inventory.",
        JsonCodec.of(RecipeLookupQuery.class, VanillaToolSchemas.RECIPE_LOOKUP_QUERY),
        JsonCodec.of(CraftingResult.class, VanillaToolSchemas.CRAFTING_RESULT),
        ToolCapabilities.supportedSingleplayer(),
        crafting::assess);
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

  private GameTool<EmptyInput, CapabilitiesSnapshot> getCapabilities(IntegrationContext context) {
    return tool(
        "minecraft.get_capabilities",
        "Returns Thread's actual registered read-only tools and active game integrations. Use this "
            + "for feature discovery instead of assuming that a tool or integration is installed.",
        emptyInputCodec(),
        JsonCodec.of(CapabilitiesSnapshot.class, VanillaToolSchemas.CAPABILITIES),
        ToolCapabilities.alwaysAvailable(),
        ignored -> ToolResult.success(capabilities(context)));
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

  private CapabilitiesSnapshot capabilities(IntegrationContext context) {
    List<String> toolIds =
        context.tools().descriptors().stream()
            .map(ToolDescriptor::id)
            .map(Object::toString)
            .toList();
    List<IntegrationCapability> activeIntegrations =
        context.integrations().integrations().stream()
            .map(info -> new IntegrationCapability(info.id().toString(), info.version()))
            .toList();
    return new CapabilitiesSnapshot(
        game.gameInfo().threadVersion(), true, toolIds, activeIntegrations);
  }

  private static JsonCodec<EmptyInput> emptyInputCodec() {
    return JsonCodec.of(EmptyInput.class, VanillaToolSchemas.EMPTY_INPUT);
  }

  private void register(IntegrationContext context, GameTool<?, ?> tool) {
    if (enabledTools.test(tool.id())) {
      context.tools().register(tool);
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
