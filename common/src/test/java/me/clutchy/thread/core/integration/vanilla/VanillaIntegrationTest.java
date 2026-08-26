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
import me.clutchy.thread.core.integration.IntegrationCandidate;
import me.clutchy.thread.core.integration.IntegrationEnvironment;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.IntegrationRegistry;
import me.clutchy.thread.core.integration.ReflectiveIntegrationLoader;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.integration.testing.ProofIntegration;
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
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockEntityItemInfo;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.ContainerInspectionQuery;
import me.clutchy.thread.core.model.world.EntityClassification;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyContainerSnapshotResult;
import me.clutchy.thread.core.model.world.NearbyContainerSummary;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.model.world.Position;
import me.clutchy.thread.core.provider.GameProvider;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.provider.RecipeProvider;
import me.clutchy.thread.core.provider.WorldProvider;
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
          "minecraft.find_item",
          "minecraft.get_capabilities",
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
          "minecraft.inspect_container",
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

    JsonObject containers =
        invoke(catalog.tools(), "minecraft.get_nearby_containers", "{\"radius\":12,\"limit\":8}");
    assertEquals("minecraft:chest", first(containers, "containers").get("blockId").getAsString());
    assertEquals(27, first(containers, "containers").get("slotCount").getAsInt());
    JsonObject inspected =
        invoke(
            catalog.tools(),
            "minecraft.inspect_container",
            "{\"position\":{\"x\":2,\"y\":64,\"z\":0}}");
    assertEquals("minecraft:chest", inspected.get("blockId").getAsString());
    assertEquals(27, inspected.getAsJsonObject("blockEntity").get("inventorySize").getAsInt());

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

    JsonObject found =
        invoke(
            catalog.tools(),
            "minecraft.find_item",
            "{\"query\":\"minecraft:diamond\",\"radius\":16,"
                + "\"containerLimit\":8,\"itemLimit\":16}");
    JsonObject foundDiamond = first(found, "matches");
    assertEquals(
        "minecraft:diamond", foundDiamond.getAsJsonObject("item").get("itemId").getAsString());
    assertEquals(5, foundDiamond.get("totalCount").getAsInt());
    assertEquals(
        List.of("PLAYER_INVENTORY", "NEARBY_CONTAINER"),
        foundDiamond.getAsJsonArray("sources").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .map(source -> source.get("sourceType").getAsString())
            .toList());

    JsonObject capabilities = invoke(catalog.tools(), "minecraft.get_capabilities", "{}");
    assertTrue(capabilities.get("readOnly").getAsBoolean());
    assertEquals(V1_TOOL_IDS, strings(capabilities, "tools"));
    assertEquals("vanilla", first(capabilities, "integrations").get("id").getAsString());
    assertEquals("1", first(capabilities, "integrations").get("version").getAsString());
  }

  @Test
  void craftingSchemasDocumentTheOptionalScopeAndItsDefault() {
    Catalog catalog = catalog(new SupportedGameProvider(), new FakePlayerProvider());

    for (String toolId :
        List.of(
            "minecraft.can_craft",
            "minecraft.get_missing_ingredients",
            "minecraft.get_crafting_plan")) {
      ToolDescriptor descriptor =
          catalog.tools().descriptors().stream()
              .filter(candidate -> candidate.id().toString().equals(toolId))
              .findFirst()
              .orElseThrow();
      JsonObject input = descriptor.inputSchema().document();
      assertFalse(
          input.getAsJsonArray("required").asList().stream()
              .map(JsonElement::getAsString)
              .anyMatch("scope"::equals),
          toolId);
      JsonObject scope = input.getAsJsonObject("properties").getAsJsonObject("scope");
      assertEquals("PLAYER_ONLY", scope.get("default").getAsString(), toolId);
      assertEquals(
          List.of("PLAYER_ONLY", "PLAYER_AND_NEARBY"),
          scope.getAsJsonArray("enum").asList().stream().map(JsonElement::getAsString).toList(),
          toolId);
      assertTrue(scope.get("description").getAsString().contains("PLAYER_ONLY"), toolId);
      assertTrue(scope.get("description").getAsString().contains("PLAYER_AND_NEARBY"), toolId);
      assertTrue(scope.get("description").getAsString().contains("incomplete"), toolId);
    }
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
    assertEquals(ToolAvailability.SUPPORTED_SINGLEPLAYER, availability.get("minecraft.find_item"));

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
        16, strings(invoke(catalog.tools(), "minecraft.get_capabilities", "{}"), "tools").size());
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
    assertInvalid(catalog.tools(), "minecraft.get_nearby_containers", "{\"radius\":0,\"limit\":8}");
    assertInvalid(catalog.tools(), "minecraft.inspect_container", "{\"position\":null}");
    assertInvalid(catalog.tools(), "minecraft.get_recipe", "{\"itemId\":\"not a registry id\"}");
    assertInvalid(catalog.tools(), "minecraft.can_craft", "{\"itemId\":\"not a registry id\"}");
    assertInvalid(
        catalog.tools(),
        "minecraft.can_craft",
        "{\"itemId\":\"minecraft:stick\",\"scope\":\"EVERYWHERE\"}");
    assertInvalid(
        catalog.tools(), "minecraft.get_missing_ingredients", "{\"itemId\":\"not a registry id\"}");
    assertInvalid(
        catalog.tools(), "minecraft.get_crafting_plan", "{\"itemId\":\"not a registry id\"}");
    assertInvalid(catalog.tools(), "minecraft.search_items", "{\"query\":\"\",\"limit\":0}");
    assertInvalid(
        catalog.tools(),
        "minecraft.find_item",
        "{\"query\":\"\",\"radius\":0,\"containerLimit\":0,\"itemLimit\":0}");
  }

  @Test
  void craftingScopeDefaultsToPlayerOnlyAndAcceptsTheExplicitEquivalent() {
    Catalog catalog = catalog(new SupportedGameProvider(), new FakePlayerProvider());

    JsonObject omitted =
        invoke(
            catalog.tools(), "minecraft.can_craft", "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
    JsonObject explicit =
        invoke(
            catalog.tools(),
            "minecraft.can_craft",
            "{\"itemId\":\"minecraft:diamond_pickaxe\",\"scope\":\"PLAYER_ONLY\"}");

    assertEquals(omitted, explicit);
    assertEquals("PLAYER_ONLY", omitted.get("scope").getAsString());
    assertTrue(omitted.getAsJsonObject("sourceStatus").get("complete").getAsBoolean());
    assertTrue(omitted.getAsJsonObject("sourceStatus").get("nearbyRadius").isJsonNull());
  }

  @Test
  void craftingToolsPreserveNoWorldAndMultiplayerFailures() {
    assertToolFailure(
        catalog(
                new MenuGameProvider(),
                new UnavailablePlayerProvider(ToolErrorCode.WORLD_NOT_AVAILABLE),
                new UnavailableRecipeProvider(ToolErrorCode.WORLD_NOT_AVAILABLE),
                ignored -> true)
            .tools(),
        "minecraft.can_craft",
        ToolErrorCode.WORLD_NOT_AVAILABLE);
    assertToolFailure(
        catalog(
                new MultiplayerGameProvider(),
                new UnavailablePlayerProvider(ToolErrorCode.UNSUPPORTED),
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
    assertToolFailure(
        catalog(
                new MenuGameProvider(),
                new UnavailablePlayerProvider(ToolErrorCode.WORLD_NOT_AVAILABLE),
                new UnavailableWorldProvider(ToolErrorCode.WORLD_NOT_AVAILABLE),
                new FakeRecipeProvider(),
                ignored -> true)
            .tools(),
        "minecraft.can_craft",
        "{\"itemId\":\"minecraft:diamond_pickaxe\",\"scope\":\"PLAYER_AND_NEARBY\"}",
        ToolErrorCode.WORLD_NOT_AVAILABLE);
    assertToolFailure(
        catalog(
                new MultiplayerGameProvider(),
                new UnavailablePlayerProvider(ToolErrorCode.UNSUPPORTED),
                new UnavailableWorldProvider(ToolErrorCode.UNSUPPORTED),
                new FakeRecipeProvider(),
                ignored -> true)
            .tools(),
        "minecraft.get_crafting_plan",
        "{\"itemId\":\"minecraft:diamond_pickaxe\",\"scope\":\"PLAYER_AND_NEARBY\"}",
        ToolErrorCode.UNSUPPORTED);
  }

  @Test
  void containerToolsPreserveNoWorldAndMultiplayerFailures() {
    Catalog noWorld =
        catalog(
            new MenuGameProvider(),
            new UnavailablePlayerProvider(ToolErrorCode.WORLD_NOT_AVAILABLE),
            new UnavailableWorldProvider(ToolErrorCode.WORLD_NOT_AVAILABLE),
            new FakeRecipeProvider(),
            ignored -> true);
    assertToolFailure(
        noWorld.tools(),
        "minecraft.get_nearby_containers",
        "{\"radius\":8,\"limit\":8}",
        ToolErrorCode.WORLD_NOT_AVAILABLE);
    assertToolFailure(
        noWorld.tools(),
        "minecraft.find_item",
        "{\"query\":\"coal\",\"radius\":8,\"containerLimit\":8,\"itemLimit\":16}",
        ToolErrorCode.WORLD_NOT_AVAILABLE);

    Catalog multiplayer =
        catalog(
            new SupportedGameProvider(),
            new UnavailablePlayerProvider(ToolErrorCode.UNSUPPORTED),
            new UnavailableWorldProvider(ToolErrorCode.UNSUPPORTED),
            new FakeRecipeProvider(),
            ignored -> true);
    assertToolFailure(
        multiplayer.tools(),
        "minecraft.inspect_container",
        "{\"position\":{\"x\":0,\"y\":64,\"z\":0}}",
        ToolErrorCode.UNSUPPORTED);
    assertToolFailure(
        multiplayer.tools(),
        "minecraft.find_item",
        "{\"query\":\"coal\",\"radius\":8,\"containerLimit\":8,\"itemLimit\":16}",
        ToolErrorCode.UNSUPPORTED);
  }

  @Test
  void nearbyItemsAffectCraftingOnlyWhenExplicitlyRequested() {
    PlayerProvider partialPlayer =
        new FakePlayerProvider() {
          @Override
          public ToolResult<InventorySnapshot> inventory() {
            return ToolResult.success(
                new InventorySnapshot(
                    0,
                    List.of(
                        new InventorySlotInfo(0, item("minecraft:diamond", "Diamond", 1, 64)),
                        new InventorySlotInfo(1, item("minecraft:stick", "Stick", 2, 64)))));
          }

          @Override
          public ToolResult<EquipmentSnapshot> equipment() {
            return ToolResult.success(equipmentWithMainHand(null));
          }
        };
    Catalog catalog = catalog(new SupportedGameProvider(), partialPlayer);

    JsonObject found =
        invoke(
            catalog.tools(),
            "minecraft.find_item",
            "{\"query\":\"minecraft:diamond\",\"radius\":8,"
                + "\"containerLimit\":8,\"itemLimit\":16}");

    assertEquals(3, first(found, "matches").get("totalCount").getAsInt());
    for (String tool :
        List.of(
            "minecraft.can_craft",
            "minecraft.get_missing_ingredients",
            "minecraft.get_crafting_plan")) {
      JsonObject playerOnly =
          invoke(catalog.tools(), tool, "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
      JsonObject expanded =
          invoke(
              catalog.tools(),
              tool,
              "{\"itemId\":\"minecraft:diamond_pickaxe\"," + "\"scope\":\"PLAYER_AND_NEARBY\"}");
      assertFalse(playerOnly.get("craftable").getAsBoolean(), tool);
      assertEquals("PLAYER_ONLY", playerOnly.get("scope").getAsString(), tool);
      assertTrue(expanded.get("craftable").getAsBoolean(), tool);
      assertEquals("PLAYER_AND_NEARBY", expanded.get("scope").getAsString(), tool);
      assertEquals(
          16, expanded.getAsJsonObject("sourceStatus").get("nearbyRadius").getAsDouble(), tool);
    }
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
    IntegrationCandidate proofCandidate =
        new IntegrationCandidate(
            IntegrationId.of("proof"), "proof-mod", ">=2", ProofIntegration.class.getName());
    catalog
        .integrations()
        .discover(
            List.of(proofCandidate),
            new IntegrationEnvironment() {
              @Override
              public Optional<String> loadedModVersion(String modId) {
                return Optional.of("2.0");
              }

              @Override
              public boolean versionCompatible(String modId, String versionRequirement) {
                return true;
              }
            },
            ignored -> true,
            new ReflectiveIntegrationLoader(ProofIntegration.class.getClassLoader()));

    JsonObject capabilities = invoke(catalog.tools(), "minecraft.get_capabilities", "{}");

    assertEquals(17, strings(capabilities, "tools").size());
    assertTrue(strings(capabilities, "tools").contains("proof.echo"));
    assertEquals(
        List.of("proof", "vanilla"),
        capabilities.getAsJsonArray("integrations").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .map(value -> value.get("id").getAsString())
            .toList());
    JsonObject proof =
        capabilities.getAsJsonArray("integrations").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .filter(value -> value.get("id").getAsString().equals("proof"))
            .findFirst()
            .orElseThrow();
    assertTrue(
        proof.getAsJsonArray("metadata").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .anyMatch(
                entry ->
                    entry.get("key").getAsString().equals("proof.mode")
                        && entry.get("value").getAsString().equals("test")));
    assertTrue(
        proof.getAsJsonArray("metadata").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .anyMatch(
                entry ->
                    entry.get("key").getAsString().equals("thread.target_mod")
                        && entry.get("value").getAsString().equals("proof-mod")));
  }

  @Test
  void absentOptionalIntegrationLeavesVanillaCapabilitiesIntact() {
    Catalog catalog = catalog(new SupportedGameProvider(), new FakePlayerProvider());
    catalog
        .integrations()
        .discover(
            List.of(
                new IntegrationCandidate(
                    IntegrationId.of("absent"),
                    "absent-mod",
                    ">=1",
                    "missing.optional.Integration")),
            new IntegrationEnvironment() {
              @Override
              public Optional<String> loadedModVersion(String modId) {
                return Optional.empty();
              }

              @Override
              public boolean versionCompatible(String modId, String versionRequirement) {
                throw new AssertionError("an absent mod must not reach version matching");
              }
            },
            ignored -> true,
            new ReflectiveIntegrationLoader(getClass().getClassLoader()));

    JsonObject capabilities = invoke(catalog.tools(), "minecraft.get_capabilities", "{}");

    assertEquals(16, strings(capabilities, "tools").size());
    assertEquals(
        List.of("vanilla"),
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
    return catalog(game, player, new FakeWorldProvider(), recipes, enabledTools);
  }

  private static Catalog catalog(
      GameProvider game,
      PlayerProvider player,
      WorldProvider world,
      RecipeProvider recipes,
      Predicate<ToolId> enabledTools) {
    ToolRegistry tools = new ToolRegistry();
    IntegrationRegistry integrations =
        new IntegrationRegistry(tools, new ContextRegistry(), new IntegrationExtensionRegistry());
    integrations.register(
        new VanillaIntegration(
            game,
            player,
            world,
            recipes,
            new NearbyContainerQuery(16, 64),
            enabledTools,
            () -> integrations.capabilities(game.gameInfo().threadVersion())));
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
    assertToolFailure(tools, toolId, "{\"itemId\":\"minecraft:diamond_pickaxe\"}", expectedCode);
  }

  private static void assertToolFailure(
      ToolRegistry tools, String toolId, String input, ToolErrorCode expectedCode) {
    ToolResult<JsonElement> result = tools.invoke(toolId, object(input));
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

    @Override
    public ToolResult<NearbyContainerResult> nearbyContainers(NearbyContainerQuery query) {
      return ToolResult.success(
          new NearbyContainerResult(
              query.radius(),
              query.limit(),
              false,
              List.of(
                  new NearbyContainerSummary(
                      "minecraft:chest",
                      "minecraft:chest",
                      "Chest",
                      new BlockPosition(2, 64, 0),
                      2,
                      27,
                      0,
                      List.of(),
                      false))));
    }

    @Override
    public ToolResult<NearbyContainerSnapshotResult> nearbyContainerSnapshots(
        NearbyContainerQuery query) {
      return ToolResult.success(
          new NearbyContainerSnapshotResult(
              query.radius(),
              query.limit(),
              false,
              List.of(
                  new BlockInfo(
                      "minecraft:chest",
                      "Chest",
                      new BlockPosition(2, 64, 0),
                      Map.of("type", "single"),
                      2,
                      true,
                      new BlockEntityInfo(
                          "minecraft:chest",
                          27,
                          List.of(
                              new BlockEntityItemInfo(
                                  "3", item("minecraft:diamond", "Diamond", 2, 64))),
                          Map.of("contentsResolved", "true"))))));
    }

    @Override
    public ToolResult<BlockInfo> inspectContainer(ContainerInspectionQuery query) {
      return ToolResult.success(
          new BlockInfo(
              "minecraft:chest",
              "Chest",
              query.position(),
              Map.of("type", "single"),
              2,
              true,
              new BlockEntityInfo("minecraft:chest", 27, List.of(), Map.of())));
    }
  }

  private static final class UnavailableWorldProvider implements WorldProvider {
    private final ToolError error;

    private UnavailableWorldProvider(ToolErrorCode code) {
      error = ToolError.of(code, "Unavailable for test.", true);
    }

    @Override
    public ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query) {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<NearbyContainerResult> nearbyContainers(NearbyContainerQuery query) {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<NearbyContainerSnapshotResult> nearbyContainerSnapshots(
        NearbyContainerQuery query) {
      return ToolResult.failure(error);
    }

    @Override
    public ToolResult<BlockInfo> inspectContainer(ContainerInspectionQuery query) {
      return ToolResult.failure(error);
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
