package me.clutchy.thread.core.integration.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import me.clutchy.thread.core.context.ContextRegistry;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.integration.GameIntegration;
import me.clutchy.thread.core.integration.IntegrationContext;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.IntegrationRegistry;
import me.clutchy.thread.core.model.game.GameInfo;
import me.clutchy.thread.core.model.game.SessionState;
import me.clutchy.thread.core.model.game.SessionStatus;
import me.clutchy.thread.core.model.game.SessionStatusReason;
import me.clutchy.thread.core.model.item.ItemComponentsInfo;
import me.clutchy.thread.core.model.item.ItemDurabilityInfo;
import me.clutchy.thread.core.model.item.ItemEnchantmentInfo;
import me.clutchy.thread.core.model.item.ItemInfo;
import me.clutchy.thread.core.model.item.ItemSearchResult;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.player.EquipmentSlotInfo;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySlotInfo;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.recipe.RecipeInfo;
import me.clutchy.thread.core.model.recipe.RecipeIngredientInfo;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.EntityClassification;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.model.world.Position;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.testing.TestJsonContracts;
import me.clutchy.thread.core.tool.ToolAvailability;
import me.clutchy.thread.core.tool.ToolDescriptor;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class VanillaIntegrationTest {
  private static final List<String> V1_TOOL_IDS =
      List.of(
          "minecraft.can_craft",
          "minecraft.get_capabilities",
          "minecraft.get_crafting_plan",
          "minecraft.get_equipment",
          "minecraft.get_game_info",
          "minecraft.get_inventory",
          "minecraft.get_missing_ingredients",
          "minecraft.get_nearby_entities",
          "minecraft.get_player",
          "minecraft.get_recipe",
          "minecraft.get_status",
          "minecraft.get_target_block",
          "minecraft.search_items");

  @Test
  void registersAndInvokesTheCompleteStructuredV1Catalog() {
    Catalog catalog = catalog(new SupportedGameProvider(), new FakePlayerProvider());

    List<ToolDescriptor> descriptors = catalog.tools().descriptors();
    assertEquals(V1_TOOL_IDS, descriptors.stream().map(tool -> tool.id().toString()).toList());
    assertTrue(descriptors.stream().allMatch(tool -> tool.capabilities().readOnly()));
    assertTrue(descriptors.stream().allMatch(tool -> tool.description().contains("Use this")));
    assertTrue(descriptors.stream().allMatch(tool -> tool.description().length() >= 120));
    assertTrue(
        descriptors.stream()
            .allMatch(
                tool -> tool.inputSchema().document().get("type").getAsString().equals("object")));
    assertTrue(
        descriptors.stream()
            .allMatch(
                tool -> tool.outputSchema().document().get("type").getAsString().equals("object")));

    JsonObject status = invoke(catalog.tools(), "minecraft.get_status", "{}");
    assertEquals("SINGLEPLAYER", status.get("state").getAsString());
    assertTrue(status.get("supported").getAsBoolean());

    JsonObject gameInfo = invoke(catalog.tools(), "minecraft.get_game_info", "{}");
    assertEquals("26.2", gameInfo.get("minecraftVersion").getAsString());
    assertEquals("0.1.0", gameInfo.get("threadVersion").getAsString());

    JsonObject player = invoke(catalog.tools(), "minecraft.get_player", "{}");
    assertEquals("minecraft:overworld", player.get("dimension").getAsString());
    assertEquals(18, player.get("health").getAsDouble());

    JsonObject inventory = invoke(catalog.tools(), "minecraft.get_inventory", "{}");
    assertEquals(
        "minecraft:diamond",
        inventory
            .getAsJsonArray("slots")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("stack")
            .get("itemId")
            .getAsString());

    JsonObject equipment = invoke(catalog.tools(), "minecraft.get_equipment", "{}");
    JsonObject mainHand = equipmentSlot(equipment, "MAIN_HAND").getAsJsonObject("item");
    assertEquals("minecraft:diamond_pickaxe", mainHand.get("itemId").getAsString());
    assertEquals("Workhorse", mainHand.get("customName").getAsString());
    assertEquals(1500, mainHand.getAsJsonObject("durability").get("remaining").getAsInt());
    assertEquals(
        "minecraft:efficiency",
        mainHand
            .getAsJsonArray("enchantments")
            .get(0)
            .getAsJsonObject()
            .get("enchantmentId")
            .getAsString());
    assertTrue(equipmentSlot(equipment, "HEAD").get("item").isJsonNull());

    JsonObject target = invoke(catalog.tools(), "minecraft.get_target_block", "{}");
    assertEquals("minecraft:stone", target.get("blockId").getAsString());
    assertEquals("Stone", target.get("displayName").getAsString());
    assertFalse(target.get("blockEntityPresent").getAsBoolean());

    JsonObject entities =
        invoke(catalog.tools(), "minecraft.get_nearby_entities", "{\"radius\":16,\"limit\":8}");
    assertEquals(16, entities.get("radius").getAsDouble());
    assertEquals("minecraft:zombie", first(entities, "entities").get("entityType").getAsString());
    assertTrue(first(entities, "entities").get("living").getAsBoolean());
    assertEquals(20, first(entities, "entities").get("maxHealth").getAsDouble());
    assertEquals("HOSTILE", first(entities, "entities").get("classification").getAsString());

    JsonObject recipe =
        invoke(
            catalog.tools(), "minecraft.get_recipe", "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
    assertEquals("minecraft:diamond_pickaxe", recipe.get("itemId").getAsString());
    assertEquals(
        "minecraft:diamond_pickaxe",
        first(recipe, "recipes").getAsJsonObject("result").get("itemId").getAsString());

    JsonObject craftable =
        invoke(
            catalog.tools(), "minecraft.can_craft", "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
    assertTrue(craftable.get("craftable").getAsBoolean());
    assertTrue(first(craftable, "recipes").get("craftable").getAsBoolean());

    JsonObject missing =
        invoke(
            catalog.tools(),
            "minecraft.get_missing_ingredients",
            "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
    assertTrue(
        first(missing, "recipes").getAsJsonArray("ingredients").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .allMatch(ingredient -> ingredient.get("missing").getAsInt() == 0));

    JsonObject plan =
        invoke(
            catalog.tools(),
            "minecraft.get_crafting_plan",
            "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
    assertTrue(plan.get("craftable").getAsBoolean());
    assertEquals("minecraft:diamond_pickaxe", first(plan, "steps").get("itemId").getAsString());
    assertTrue(plan.getAsJsonArray("missingMaterials").isEmpty());
    assertTrue(plan.getAsJsonArray("issues").isEmpty());

    JsonObject search =
        invoke(
            catalog.tools(), "minecraft.search_items", "{\"query\":\"diamond pick\",\"limit\":10}");
    assertEquals("diamond pick", search.get("query").getAsString());
    assertEquals("minecraft:diamond_pickaxe", first(search, "items").get("itemId").getAsString());

    JsonObject capabilities = invoke(catalog.tools(), "minecraft.get_capabilities", "{}");
    assertTrue(capabilities.get("readOnly").getAsBoolean());
    assertEquals(V1_TOOL_IDS, strings(capabilities, "tools"));
    assertEquals("vanilla", first(capabilities, "integrations").get("id").getAsString());
    assertEquals("1", first(capabilities, "integrations").get("version").getAsString());
  }

  @Test
  void advertisesAccurateAvailabilityAndKeepsMenuSafeToolsCallable() {
    Catalog catalog = catalog(new MenuGameProvider(), new FailingPlayerProvider());

    Map<String, ToolAvailability> availability =
        catalog.tools().descriptors().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    tool -> tool.id().toString(), tool -> tool.capabilities().availability()));
    assertEquals(ToolAvailability.ALWAYS, availability.get("minecraft.get_status"));
    assertEquals(ToolAvailability.ALWAYS, availability.get("minecraft.get_game_info"));
    assertEquals(ToolAvailability.ALWAYS, availability.get("minecraft.search_items"));
    assertEquals(ToolAvailability.ALWAYS, availability.get("minecraft.get_capabilities"));
    assertEquals(
        ToolAvailability.SUPPORTED_SINGLEPLAYER, availability.get("minecraft.get_inventory"));

    JsonObject status = invoke(catalog.tools(), "minecraft.get_status", "{}");
    assertEquals("MAIN_MENU", status.get("state").getAsString());
    assertFalse(status.get("supported").getAsBoolean());
    assertEquals("NO_WORLD", status.get("reason").getAsString());
    assertEquals(
        "26.2",
        invoke(catalog.tools(), "minecraft.get_game_info", "{}")
            .get("minecraftVersion")
            .getAsString());
    assertEquals(
        13, strings(invoke(catalog.tools(), "minecraft.get_capabilities", "{}"), "tools").size());
    assertEquals(
        "minecraft:diamond_pickaxe",
        first(
                invoke(
                    catalog.tools(),
                    "minecraft.search_items",
                    "{\"query\":\"diamond pick\",\"limit\":10}"),
                "items")
            .get("itemId")
            .getAsString());
  }

  @Test
  void rejectsMalformedInputsBeforeCallingProviders() {
    Catalog catalog = catalog(new SupportedGameProvider(), new FakePlayerProvider());

    assertInvalid(catalog.tools(), "minecraft.get_status", "{\"extra\":true}");
    assertInvalid(catalog.tools(), "minecraft.get_nearby_entities", "{\"radius\":0,\"limit\":8}");
    assertInvalid(catalog.tools(), "minecraft.get_recipe", "{\"itemId\":\"not a registry id\"}");
    assertInvalid(catalog.tools(), "minecraft.can_craft", "{\"itemId\":\"not a registry id\"}");
    assertInvalid(
        catalog.tools(), "minecraft.get_missing_ingredients", "{\"itemId\":\"not a registry id\"}");
    assertInvalid(
        catalog.tools(), "minecraft.get_crafting_plan", "{\"itemId\":\"not a registry id\"}");
    assertInvalid(catalog.tools(), "minecraft.search_items", "{\"query\":\"\",\"limit\":0}");
  }

  @Test
  void craftingToolsPreserveNoWorldAndMultiplayerFailures() {
    assertToolFailure(
        catalog(
                new MenuGameProvider(),
                new FailingPlayerProvider(),
                new UnavailableRecipeProvider(ToolErrorCode.WORLD_NOT_AVAILABLE),
                ignored -> true)
            .tools(),
        "minecraft.can_craft",
        ToolErrorCode.WORLD_NOT_AVAILABLE);
    assertToolFailure(
        catalog(
                new MultiplayerGameProvider(),
                new FailingPlayerProvider(),
                new UnavailableRecipeProvider(ToolErrorCode.UNSUPPORTED),
                ignored -> true)
            .tools(),
        "minecraft.get_missing_ingredients",
        ToolErrorCode.UNSUPPORTED);
    assertToolFailure(
        catalog(
                new MenuGameProvider(),
                new UnavailablePlayerProvider(ToolErrorCode.WORLD_NOT_AVAILABLE),
                new UnavailableRecipeProvider(ToolErrorCode.WORLD_NOT_AVAILABLE),
                ignored -> true)
            .tools(),
        "minecraft.get_crafting_plan",
        ToolErrorCode.WORLD_NOT_AVAILABLE);
    assertToolFailure(
        catalog(
                new MultiplayerGameProvider(),
                new UnavailablePlayerProvider(ToolErrorCode.UNSUPPORTED),
                new UnavailableRecipeProvider(ToolErrorCode.UNSUPPORTED),
                ignored -> true)
            .tools(),
        "minecraft.get_crafting_plan",
        ToolErrorCode.UNSUPPORTED);
  }

  @Test
  void targetAbsenceIsAStableStructuredNotFoundResult() {
    Catalog catalog = catalog(new SupportedGameProvider(), new EmptyTargetPlayerProvider());

    ToolResult<JsonElement> result =
        catalog.tools().invoke("minecraft.get_target_block", object("{}"));

    assertFalse(result.successful());
    assertEquals(me.clutchy.thread.core.error.ToolErrorCode.NOT_FOUND, result.error().code());
    assertTrue(result.error().retryable());
  }

  @Test
  void capabilitiesAreDerivedFromLiveRegistrations() {
    Catalog catalog = catalog(new SupportedGameProvider(), new FakePlayerProvider());
    catalog.integrations().register(new ExampleIntegration());

    JsonObject capabilities = invoke(catalog.tools(), "minecraft.get_capabilities", "{}");

    assertEquals(14, strings(capabilities, "tools").size());
    assertTrue(strings(capabilities, "tools").contains("example.echo"));
    assertEquals(
        List.of("example", "vanilla"),
        capabilities.getAsJsonArray("integrations").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .map(value -> value.get("id").getAsString())
            .toList());
  }

  @Test
  void disabledToolsNeverEnterDiscoveryOrCapabilities() {
    Set<String> enabled = Set.of("minecraft.get_status", "minecraft.get_capabilities");
    Catalog catalog =
        catalog(
            new SupportedGameProvider(),
            new FakePlayerProvider(),
            toolId -> enabled.contains(toolId.value()));

    assertEquals(
        List.of("minecraft.get_capabilities", "minecraft.get_status"),
        catalog.tools().descriptors().stream().map(tool -> tool.id().value()).toList());
    assertEquals(
        List.of("minecraft.get_capabilities", "minecraft.get_status"),
        strings(invoke(catalog.tools(), "minecraft.get_capabilities", "{}"), "tools"));
    assertEquals(
        me.clutchy.thread.core.error.ToolErrorCode.NOT_FOUND,
        catalog.tools().invoke("minecraft.get_player", object("{}")).error().code());
  }

  private static Catalog catalog(GameProvider game, PlayerProvider player) {
    return catalog(game, player, ignored -> true);
  }

  private static Catalog catalog(
      GameProvider game, PlayerProvider player, Predicate<ToolId> enabledTools) {
    return catalog(game, player, new FakeRecipeProvider(), enabledTools);
  }

  private static Catalog catalog(
      GameProvider game,
      PlayerProvider player,
      RecipeProvider recipes,
      Predicate<ToolId> enabledTools) {
    ToolRegistry tools = new ToolRegistry();
    IntegrationRegistry integrations = new IntegrationRegistry(tools, new ContextRegistry());
    integrations.register(
        new VanillaIntegration(game, player, new FakeWorldProvider(), recipes, enabledTools));
    return new Catalog(tools, integrations);
  }

  private static JsonObject invoke(ToolRegistry tools, String toolId, String input) {
    ToolResult<JsonElement> result = tools.invoke(toolId, object(input));
    assertTrue(
        result.successful(),
        () -> toolId + " failed: " + (result.error() == null ? "unknown" : result.error()));
    assertTrue(result.value().isJsonObject(), () -> toolId + " did not return an object");
    return result.value().getAsJsonObject();
  }

  private static void assertInvalid(ToolRegistry tools, String toolId, String input) {
    ToolResult<JsonElement> result = tools.invoke(toolId, object(input));
    assertFalse(result.successful());
    assertEquals(me.clutchy.thread.core.error.ToolErrorCode.INVALID_INPUT, result.error().code());
  }

  private static void assertToolFailure(
      ToolRegistry tools, String toolId, ToolErrorCode expectedCode) {
    ToolResult<JsonElement> result =
        tools.invoke(toolId, object("{\"itemId\":\"minecraft:diamond_pickaxe\"}"));
    assertFalse(result.successful());
    assertEquals(expectedCode, result.error().code());
  }

  private static JsonElement object(String json) {
    return JsonParser.parseString(json);
  }

  private static JsonObject first(JsonObject value, String property) {
    return value.getAsJsonArray(property).get(0).getAsJsonObject();
  }

  private static JsonObject equipmentSlot(JsonObject equipment, String slot) {
    return equipment.getAsJsonArray("slots").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("slot").getAsString().equals(slot))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> strings(JsonObject value, String property) {
    return value.getAsJsonArray(property).asList().stream().map(JsonElement::getAsString).toList();
  }

  private record Catalog(ToolRegistry tools, IntegrationRegistry integrations) {}

  private static final class SupportedGameProvider implements GameProvider {
    @Override
    public SessionStatus sessionStatus() {
      return new SessionStatus(SessionState.SINGLEPLAYER, true, true, true, null);
    }

    @Override
    public GameInfo gameInfo() {
      return new GameInfo("26.2", "fabric", "0.19.3", "0.1.0");
    }
  }

  private static final class MenuGameProvider implements GameProvider {
    @Override
    public SessionStatus sessionStatus() {
      return new SessionStatus(
          SessionState.MAIN_MENU, false, false, false, SessionStatusReason.NO_WORLD);
    }

    @Override
    public GameInfo gameInfo() {
      return new GameInfo("26.2", "fabric", "0.19.3", "0.1.0");
    }
  }

  private static final class MultiplayerGameProvider implements GameProvider {
    @Override
    public SessionStatus sessionStatus() {
      return new SessionStatus(
          SessionState.MULTIPLAYER, true, true, false, SessionStatusReason.MULTIPLAYER_UNSUPPORTED);
    }

    @Override
    public GameInfo gameInfo() {
      return new GameInfo("26.2", "fabric", "0.19.3", "0.1.0");
    }
  }

  private static class FakePlayerProvider implements PlayerProvider {
    private static final ItemStackInfo PICKAXE =
        new ItemStackInfo(
            "minecraft:diamond_pickaxe",
            "Diamond Pickaxe",
            "Workhorse",
            1,
            1,
            new ItemDurabilityInfo(1500, 1561, 61),
            List.of(
                new ItemEnchantmentInfo("minecraft:unbreaking", 3),
                new ItemEnchantmentInfo("minecraft:efficiency", 5)),
            new ItemComponentsInfo(null, false, 2, List.of("Mining tool"), null, 0));

    @Override
    public ToolResult<PlayerStatus> status() {
      return ToolResult.success(
          new PlayerStatus(
              18,
              20,
              14,
              3.5,
              21,
              0.42,
              new Position(152.2, 67, -381.7),
              "minecraft:overworld",
              "survival"));
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
      return ToolResult.success(
          new InventorySnapshot(
              0,
              List.of(
                  new InventorySlotInfo(0, item("minecraft:diamond", "Diamond", 3, 64)),
                  new InventorySlotInfo(1, item("minecraft:stick", "Stick", 2, 64)))));
    }

    @Override
    public ToolResult<EquipmentSnapshot> equipment() {
      return ToolResult.success(equipmentWithMainHand(PICKAXE));
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      return ToolResult.success(
          Optional.of(
              new BlockInfo(
                  "minecraft:stone",
                  "Stone",
                  new BlockPosition(152, 66, -380),
                  Map.of(),
                  3.4,
                  false,
                  null)));
    }
  }

  private static final class EmptyTargetPlayerProvider extends FakePlayerProvider {
    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      return ToolResult.success(Optional.empty());
    }
  }

  private static final class FailingPlayerProvider implements PlayerProvider {
    private static AssertionError unexpected() {
      return new AssertionError("menu-safe tool called a gameplay provider");
    }

    @Override
    public ToolResult<PlayerStatus> status() {
      throw unexpected();
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
      throw unexpected();
    }

    @Override
    public ToolResult<EquipmentSnapshot> equipment() {
      throw unexpected();
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      throw unexpected();
    }
  }

  private static final class UnavailablePlayerProvider implements PlayerProvider {
    private final ToolError error;

    private UnavailablePlayerProvider(ToolErrorCode code) {
      error = ToolError.of(code, "Unavailable for test.", true);
    }

    @Override
    public ToolResult<PlayerStatus> status() {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<EquipmentSnapshot> equipment() {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      return ToolResult.failure(error);
    }
  }

  private static final class FakeWorldProvider implements WorldProvider {
    @Override
    public ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query) {
      return ToolResult.success(
          new NearbyEntityResult(
              query.radius(),
              query.limit(),
              false,
              List.of(
                  new EntityInfo(
                      "minecraft:zombie",
                      "Zombie",
                      null,
                      8.4,
                      new Position(160, 67, -380),
                      true,
                      20.0,
                      20.0,
                      EntityClassification.HOSTILE))));
    }
  }

  private static final class FakeRecipeProvider implements RecipeProvider {
    @Override
    public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
      ItemStackInfo result = item("minecraft:diamond_pickaxe", "Diamond Pickaxe", 1, 1);
      return ToolResult.success(
          List.of(
              new RecipeInfo(
                  "minecraft:diamond_pickaxe",
                  "minecraft:crafting_shaped",
                  result,
                  List.of(
                      new RecipeIngredientInfo(List.of("minecraft:diamond"), List.of(), 3),
                      new RecipeIngredientInfo(List.of("minecraft:stick"), List.of(), 2)))));
    }

    @Override
    public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
      return ToolResult.success(
          new ItemSearchResult(
              query,
              limit,
              false,
              List.of(new ItemInfo("minecraft:diamond_pickaxe", "Diamond Pickaxe"))));
    }
  }

  private static final class UnavailableRecipeProvider implements RecipeProvider {
    private final ToolError error;

    private UnavailableRecipeProvider(ToolErrorCode code) {
      error = ToolError.of(code, "Unavailable for test.", true);
    }

    @Override
    public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
      throw new AssertionError("item search was not expected");
    }
  }

  private static final class ExampleIntegration implements GameIntegration {
    @Override
    public IntegrationId id() {
      return IntegrationId.of("example");
    }

    @Override
    public String version() {
      return "2";
    }

    @Override
    public String description() {
      return "Test-only dynamic integration.";
    }

    @Override
    public void register(IntegrationContext context) {
      context.tools().register(TestJsonContracts.echoTool("example.echo"));
    }
  }

  private static ItemStackInfo item(String itemId, String displayName, int count, int maxCount) {
    return new ItemStackInfo(itemId, displayName, null, count, maxCount, null, List.of(), null);
  }

  private static EquipmentSnapshot equipmentWithMainHand(ItemStackInfo mainHand) {
    return new EquipmentSnapshot(
        List.of(
            new EquipmentSlotInfo(EquipmentPosition.MAIN_HAND, mainHand),
            new EquipmentSlotInfo(EquipmentPosition.OFF_HAND, null),
            new EquipmentSlotInfo(EquipmentPosition.HEAD, null),
            new EquipmentSlotInfo(EquipmentPosition.CHEST, null),
            new EquipmentSlotInfo(EquipmentPosition.LEGS, null),
            new EquipmentSlotInfo(EquipmentPosition.FEET, null)));
  }
}
