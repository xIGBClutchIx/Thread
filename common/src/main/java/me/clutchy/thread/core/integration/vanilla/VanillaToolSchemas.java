package me.clutchy.thread.core.integration.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.clutchy.thread.core.model.advancement.AdvancementInfo;
import me.clutchy.thread.core.model.advancement.AdvancementListQuery;
import me.clutchy.thread.core.model.item.find.FoundItemSourceType;
import me.clutchy.thread.core.model.options.ClientOptionsQuery;
import me.clutchy.thread.core.model.options.ClientOptionsSection;
import me.clutchy.thread.core.model.options.ClientOptionsSnapshot;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.NearbyContainerSummary;
import me.clutchy.thread.core.serialization.JsonSchema;
import me.clutchy.thread.core.service.item.ItemFinder;

final class VanillaToolSchemas {
  private static final String REGISTRY_ID_PATTERN = "^[a-z0-9_.-]+:[a-z0-9/._-]+$";
  private static final String TOOL_ID_PATTERN = "^[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+$";
  private static final String INTEGRATION_ID_PATTERN = "^[a-z][a-z0-9_-]*$";
  private static final String INTEGRATION_METADATA_KEY_PATTERN =
      "^[a-z][a-z0-9_-]*(?:\\.[a-z][a-z0-9_-]*)+$";

  private static final JsonObject POSITION =
      object(property("x", number()), property("y", number()), property("z", number()));
  private static final JsonObject BLOCK_POSITION =
      object(property("x", integer()), property("y", integer()), property("z", integer()));
  private static final JsonObject ITEM_INFO =
      object(property("itemId", registryId()), property("displayName", string(1, 256, null)));
  private static final JsonObject ITEM_DURABILITY =
      object(
          property("remaining", integer(0, null)),
          property("maximum", integer(1, null)),
          property("damage", integer(0, null)));
  private static final JsonObject ITEM_ENCHANTMENT =
      object(property("enchantmentId", registryId()), property("level", integer(1, null)));
  private static final JsonObject ITEM_COMPONENTS =
      object(
          property("rarity", nullable(string(1, 32, null))),
          property("unbreakable", bool()),
          property("repairCost", integer(0, null)),
          property("lore", boundedArray(string(1, 256, null), 0, 16)),
          property("potionId", nullable(registryId())),
          property("storedItemStacks", integer(0, null)));
  private static final JsonObject ITEM_STACK =
      object(
          property("itemId", registryId()),
          property("displayName", string(1, 256, null)),
          property("customName", nullable(string(1, 256, null))),
          property("count", integer(1, null)),
          property("maxCount", integer(1, null)),
          property("durability", nullable(ITEM_DURABILITY)),
          property("enchantments", array(ITEM_ENCHANTMENT)),
          property("components", nullable(ITEM_COMPONENTS)));
  private static final JsonObject INVENTORY_SLOT =
      object(
          property("slot", integer(0, InventorySnapshot.MAIN_SLOT_COUNT - 1)),
          property("stack", ITEM_STACK));
  private static final JsonObject EQUIPMENT_SLOT =
      object(
          property("slot", enumString("MAIN_HAND", "OFF_HAND", "HEAD", "CHEST", "LEGS", "FEET")),
          property("item", nullable(ITEM_STACK)));
  private static final JsonObject STATUS_EFFECT =
      object(
          property("effectId", registryId()),
          property("displayName", string(1, 256, null)),
          property("amplifier", integer(0, 255)),
          property("durationTicks", nullable(integer(0, null))),
          property("infinite", bool()),
          property("ambient", bool()),
          property("visible", bool()),
          property("showIcon", bool()));
  private static final JsonObject PLAYER_ARMOR =
      object(property("value", integer(0, null)), property("toughness", number(0.0, null)));
  private static final JsonObject PLAYER_AIR =
      object(property("current", integer()), property("maximum", integer(1, null)));
  private static final JsonObject PLAYER_MOVEMENT =
      object(
          property("sprinting", bool()),
          property("swimming", bool()),
          property("crouching", bool()),
          property("flying", bool()),
          property("onGround", bool()),
          property("fallDistance", number(0.0, null)));
  private static final JsonObject PLAYER_CONDITIONS =
      object(
          property("sleeping", bool()),
          property("onFire", bool()),
          property("freezing", bool()),
          property("fullyFrozen", bool()));
  private static final JsonObject PLAYER_VEHICLE =
      object(
          property("entityType", registryId()),
          property("displayName", string(1, 256, null)),
          property("customName", nullable(string(1, 256, null))));
  private static final JsonObject PLAYER_RESPAWN =
      object(
          property("dimension", registryId()),
          property("position", BLOCK_POSITION),
          property("forced", bool()));
  private static final JsonObject BLOCK_ENTITY_ITEM =
      object(property("slot", string(1, 64, null)), property("item", ITEM_STACK));
  private static final JsonObject BLOCK_ENTITY_INFO =
      object(
          property("typeId", registryId()),
          property("inventorySize", integer(0, null)),
          property("items", boundedArray(BLOCK_ENTITY_ITEM, 0, BlockEntityInfo.MAX_ITEMS)),
          property("state", openObject()));
  private static final JsonObject BLOCK_INFO =
      object(
          property("blockId", registryId()),
          property("displayName", string(1, 256, null)),
          property("position", BLOCK_POSITION),
          property("properties", openObject()),
          property("distance", number(0.0, null)),
          property("blockEntityPresent", bool()),
          property("blockEntity", nullable(BLOCK_ENTITY_INFO)));
  private static final JsonObject NEARBY_CONTAINER_SUMMARY =
      object(
          property("blockId", registryId()),
          property("containerTypeId", registryId()),
          property("displayName", string(1, 256, null)),
          property("position", BLOCK_POSITION),
          property("distance", number(0.0, null)),
          property("slotCount", integer(0, null)),
          property("usedSlotCount", nullable(integer(0, null))),
          property(
              "itemSummary",
              boundedArray(BLOCK_ENTITY_ITEM, 0, NearbyContainerSummary.MAX_SUMMARY_ITEMS)),
          property("itemSummaryTruncated", bool()));
  private static final JsonObject FOUND_ITEM_SOURCE =
      object(
          property(
              "sourceType",
              enumString(
                  java.util.Arrays.stream(FoundItemSourceType.values())
                      .map(Enum::name)
                      .toArray(String[]::new))),
          property("count", integer(1, null)),
          property(
              "inventorySlots",
              boundedArray(
                  integer(0, InventorySnapshot.MAIN_SLOT_COUNT - 1),
                  0,
                  InventorySnapshot.MAIN_SLOT_COUNT)),
          property(
              "equipmentSlots",
              boundedArray(
                  enumString("MAIN_HAND", "OFF_HAND", "HEAD", "CHEST", "LEGS", "FEET"), 0, 6)),
          property(
              "containerSlots", boundedArray(string(1, 64, null), 0, BlockEntityInfo.MAX_ITEMS)),
          property("containerPosition", nullable(BLOCK_POSITION)),
          property("containerTypeId", nullable(registryId())),
          property("distance", nullable(number(0.0, null))));
  private static final JsonObject FOUND_ITEM =
      object(
          property("item", ITEM_INFO),
          property("totalCount", integer(1, null)),
          property("sources", array(FOUND_ITEM_SOURCE)));
  private static final JsonObject ENTITY_INFO =
      object(
          property("entityType", registryId()),
          property("displayName", string(1, 256, null)),
          property("customName", nullable(string(1, 256, null))),
          property("distance", number(0.0, null)),
          property("position", POSITION),
          property("living", bool()),
          property("health", nullable(number(0.0, null))),
          property("maxHealth", nullable(number(0.0, null))),
          property("classification", nullableEnumString("HOSTILE", "PASSIVE", "NEUTRAL")),
          property("equipment", boundedArray(EQUIPMENT_SLOT, 0, 6)),
          property("activeEffects", boundedArray(STATUS_EFFECT, 0, EntityInfo.MAX_ACTIVE_EFFECTS)),
          property("activeEffectsTruncated", bool()),
          property("age", nullableEnumString("BABY", "ADULT")),
          property("tamed", nullable(bool())),
          property("ownerName", nullable(string(1, 256, null))),
          property("villagerProfession", nullable(registryId())),
          property("villagerLevel", nullable(integer(1, 5))));
  private static final JsonObject RECIPE_INGREDIENT =
      object(
          property("itemIds", array(registryId())),
          property("tagIds", array(registryId())),
          property("count", integer(1, null)));
  private static final JsonObject RECIPE_INFO =
      object(
          property("recipeId", registryId()),
          property("type", registryId()),
          property("result", ITEM_STACK),
          property("ingredients", array(RECIPE_INGREDIENT)));
  private static final JsonObject INGREDIENT_ALLOCATION =
      object(
          property("itemId", registryId()),
          property("count", integer(1, null)),
          property("sourceAllocations", array(FOUND_ITEM_SOURCE)));
  private static final JsonObject INGREDIENT_AVAILABILITY =
      object(
          property("itemIds", array(registryId())),
          property("tagIds", array(registryId())),
          property("required", integer(1, null)),
          property("available", integer(0, null)),
          property("missing", integer(0, null)),
          property("allocations", array(INGREDIENT_ALLOCATION)));
  private static final JsonObject RECIPE_CRAFTABILITY =
      object(
          property("variant", integer(1, null)),
          property("recipeId", registryId()),
          property("type", registryId()),
          property("resultCount", integer(1, null)),
          property("craftable", bool()),
          property("ingredients", array(INGREDIENT_AVAILABILITY)));
  private static final JsonObject CRAFTING_PLAN_STEP =
      object(
          property("step", integer(1, null)),
          property("itemId", registryId()),
          property("variant", integer(1, null)),
          property("recipeId", registryId()),
          property("type", registryId()),
          property("executions", integer(1, null)),
          property("resultCount", integer(1, null)),
          property("ingredients", array(INGREDIENT_AVAILABILITY)));
  private static final JsonObject MISSING_MATERIAL =
      object(property("itemId", registryId()), property("count", integer(1, null)));
  private static final JsonObject CRAFTING_PLAN_ISSUE =
      object(
          property("type", enumString("CYCLE", "MAX_DEPTH", "PLAN_LIMIT")),
          property("itemId", registryId()),
          property("required", integer(1, null)),
          property("path", boundedArray(registryId(), 1, 33)));
  private static final JsonObject CRAFTING_SOURCE_STATUS =
      object(
          property("complete", bool()),
          property("nearbyRadius", nullable(number(0.0, null))),
          property("nearbyContainerLimit", nullable(integer(1, null))),
          property("nearbyContainersTruncated", bool()),
          property("unresolvedContainersSkipped", integer(0, null)),
          property("contentLimitedContainersSkipped", integer(0, null)));
  private static final JsonObject ADVANCEMENT_CRITERION =
      object(
          property("name", string(1, 256, null)),
          property("completed", bool()),
          property("obtainedAt", nullable(string(1, 64, null))));
  private static final JsonObject ADVANCEMENT_SUMMARY =
      object(
          property("advancementId", registryId()),
          property("title", nullable(string(1, 512, null))),
          property("description", nullable(string(1, 2_048, null))),
          property("completed", bool()),
          property("completionPercentage", number(0.0, 100.0)),
          property("completedCriteria", integer(0, null)),
          property("totalCriteria", integer(0, null)),
          property("completedRequirements", integer(0, null)),
          property("totalRequirements", integer(0, null)),
          property("parentAdvancementId", nullable(registryId())),
          property("tabAdvancementId", nullable(registryId())),
          property("tabTitle", nullable(string(1, 512, null))),
          property("displayType", nullableEnumString("TASK", "GOAL", "CHALLENGE")),
          property("hidden", nullable(bool())),
          property("firstProgressAt", nullable(string(1, 64, null))),
          property("completedAt", nullable(string(1, 64, null))));
  private static final JsonObject ADVANCEMENT_DETAILS =
      object(
          property("advancementId", registryId()),
          property("title", nullable(string(1, 512, null))),
          property("description", nullable(string(1, 2_048, null))),
          property("completed", bool()),
          property("completionPercentage", number(0.0, 100.0)),
          property("completedCriteria", integer(0, null)),
          property("totalCriteria", integer(0, null)),
          property("completedRequirements", integer(0, null)),
          property("totalRequirements", integer(0, null)),
          property("criteriaTruncated", bool()),
          property("parentAdvancementId", nullable(registryId())),
          property("tabAdvancementId", nullable(registryId())),
          property("tabTitle", nullable(string(1, 512, null))),
          property("displayType", nullableEnumString("TASK", "GOAL", "CHALLENGE")),
          property("hidden", nullable(bool())),
          property("firstProgressAt", nullable(string(1, 64, null))),
          property("completedAt", nullable(string(1, 64, null))),
          property(
              "criteria",
              boundedArray(ADVANCEMENT_CRITERION, 0, AdvancementInfo.MAX_RETURNED_CRITERIA)));
  private static final JsonObject INTEGRATION_METADATA_ENTRY =
      object(
          property("key", string(1, 128, INTEGRATION_METADATA_KEY_PATTERN)),
          property("value", string(1, 256, null)));
  private static final JsonObject INTEGRATION_CAPABILITY =
      object(
          property("id", string(1, 64, INTEGRATION_ID_PATTERN)),
          property("version", string(1, 128, null)),
          property("metadata", boundedArray(INTEGRATION_METADATA_ENTRY, 0, 32)));
  private static final JsonObject CLIENT_OPTIONS_SECTION =
      enumString(
          java.util.Arrays.stream(ClientOptionsSection.values())
              .map(Enum::name)
              .toArray(String[]::new));
  private static final JsonObject CLIENT_OPTIONS_GENERAL =
      object(
          property("languageCode", string(1, 64, null)),
          property("mainHand", enumString("LEFT", "RIGHT")),
          property("pauseOnLostFocus", bool()),
          property("advancedItemTooltips", bool()));
  private static final JsonObject CLIENT_OPTIONS_VIDEO =
      object(
          property("fullscreen", bool()),
          property("graphicsMode", enumString("FAST", "FANCY", "FABULOUS", "CUSTOM")),
          property("renderDistance", integer(0, null)),
          property("simulationDistance", integer(0, null)),
          property("vsync", bool()),
          property("fpsLimit", integer(0, null)),
          property("guiScale", integer(0, null)),
          property("gamma", number()),
          property("particles", enumString("ALL", "DECREASED", "MINIMAL")),
          property("mipmapLevel", integer(0, null)),
          property("entityShadows", bool()),
          property("fov", integer(1, 180)));
  private static final JsonObject SOUND_CATEGORY_VOLUME =
      object(property("category", string(1, 32, null)), property("volume", number(0.0, 1.0)));
  private static final JsonObject CLIENT_OPTIONS_AUDIO =
      object(
          property("masterVolume", number(0.0, 1.0)),
          property("categoryVolumes", boundedArray(SOUND_CATEGORY_VOLUME, 0, 32)),
          property("outputDevice", nullable(string(1, 256, null))),
          property("directionalAudio", bool()));
  private static final JsonObject CLIENT_OPTIONS_CONTROLS =
      object(
          property("mouseSensitivity", number(0.0, 1.0)),
          property("invertMouseX", bool()),
          property("invertMouseY", bool()),
          property("rawInput", bool()),
          property("autoJump", bool()),
          property("crouchMode", enumString("HOLD", "TOGGLE")),
          property("sprintMode", enumString("HOLD", "TOGGLE")));
  private static final JsonObject CLIENT_OPTIONS_ACCESSIBILITY =
      object(
          property("subtitles", bool()),
          property("narrator", enumString("OFF", "ALL", "CHAT", "SYSTEM")),
          property("narratorHotkey", bool()),
          property("highContrast", bool()),
          property("highContrastBlockOutline", bool()),
          property("forceUnicodeFont", bool()),
          property("hideLightningFlashes", bool()),
          property("notificationDisplayTime", number(0.0, null)));
  private static final JsonObject CLIENT_OPTIONS_CHAT =
      object(
          property("visibility", enumString("FULL", "SYSTEM", "HIDDEN")),
          property("opacity", number(0.0, 1.0)),
          property("scale", number(0.0, 1.0)),
          property("lineSpacing", number(0.0, 1.0)),
          property("textBackgroundOpacity", number(0.0, 1.0)),
          property("backgroundForChatOnly", bool()),
          property("colors", bool()),
          property("links", bool()),
          property("linksPrompt", bool()),
          property("onlyShowSecureChat", bool()));
  private static final JsonObject CLIENT_OPTIONS_KEYBIND =
      object(
          property("actionId", string(1, 256, null)),
          property("displayName", string(1, 256, null)),
          property("categoryId", registryId()),
          property("categoryDisplayName", string(1, 256, null)),
          property("inputType", enumString("KEYBOARD", "MOUSE", "SCANCODE", "UNBOUND")),
          property("boundInput", nullable(string(1, 256, null))),
          property("boundDisplayName", nullable(string(1, 256, null))),
          property("unbound", bool()),
          property("defaultBinding", bool()),
          property(
              "conflicts",
              boundedArray(string(1, 256, null), 0, ClientOptionsSnapshot.Keybind.MAX_CONFLICTS)),
          property("conflictsTruncated", bool()));
  private static final JsonObject CLIENT_OPTIONS_KEYBINDS =
      object(
          property("totalCount", integer(0, null)),
          property("returnedCount", integer(0, ClientOptionsQuery.MAX_KEYBIND_LIMIT)),
          property("limit", integer(1, ClientOptionsQuery.MAX_KEYBIND_LIMIT)),
          property("truncated", bool()),
          property(
              "bindings",
              boundedArray(CLIENT_OPTIONS_KEYBIND, 0, ClientOptionsQuery.MAX_KEYBIND_LIMIT)));

  static final JsonSchema EMPTY_INPUT = schema(object());
  static final JsonSchema SESSION_STATUS =
      schema(
          object(
              property(
                  "state", enumString("MAIN_MENU", "LOADING_WORLD", "SINGLEPLAYER", "MULTIPLAYER")),
              property("worldLoaded", bool()),
              property("playerAvailable", bool()),
              property("supported", bool()),
              property(
                  "reason",
                  nullableEnumString(
                      "NO_WORLD",
                      "WORLD_LOADING",
                      "PLAYER_NOT_AVAILABLE",
                      "MULTIPLAYER_UNSUPPORTED"))));
  static final JsonSchema GAME_INFO =
      schema(
          object(
              property("minecraftVersion", string(1, 128, null)),
              property("loader", string(1, 64, null)),
              property("loaderVersion", string(1, 128, null)),
              property("threadVersion", string(1, 128, null))));
  static final JsonSchema CLIENT_OPTIONS_QUERY =
      schema(
          object(
              optionalProperty("sections", clientOptionsSections()),
              optionalProperty("keybindLimit", clientOptionsKeybindLimit())));
  static final JsonSchema CLIENT_OPTIONS_SNAPSHOT =
      schema(
          object(
              property(
                  "sections",
                  boundedArray(CLIENT_OPTIONS_SECTION, 1, ClientOptionsSection.values().length)),
              property("general", nullable(CLIENT_OPTIONS_GENERAL)),
              property("video", nullable(CLIENT_OPTIONS_VIDEO)),
              property("audio", nullable(CLIENT_OPTIONS_AUDIO)),
              property("controls", nullable(CLIENT_OPTIONS_CONTROLS)),
              property("accessibility", nullable(CLIENT_OPTIONS_ACCESSIBILITY)),
              property("chat", nullable(CLIENT_OPTIONS_CHAT)),
              property("keybinds", nullable(CLIENT_OPTIONS_KEYBINDS))));
  static final JsonSchema WORLD_INFO =
      schema(
          object(
              property("dimensionId", registryId()),
              property("biomeId", registryId()),
              property("biomeName", nullable(string(1, 256, null))),
              property("playerPosition", POSITION),
              property("worldSpawnDimensionId", registryId()),
              property("worldSpawnPosition", BLOCK_POSITION),
              property("distanceFromSpawn", nullable(number(0.0, null))),
              property("difficulty", enumString("peaceful", "easy", "normal", "hard")),
              property("hardcore", bool()),
              property("gameTimeTicks", integer(0, null)),
              property("dayTimeTicks", integer()),
              property("worldDay", integer()),
              property("timeOfDayTicks", integer(0, 23_999)),
              property("daylightState", enumString("DAY", "NIGHT", "FIXED")),
              property("raining", bool()),
              property("thundering", bool()),
              property("localLightLevel", integer(0, 15)),
              property(
                  "moonPhase",
                  enumString(
                      "full_moon",
                      "waning_gibbous",
                      "third_quarter",
                      "waning_crescent",
                      "new_moon",
                      "waxing_crescent",
                      "first_quarter",
                      "waxing_gibbous")),
              property("biomeTemperature", number()),
              property("biomeHasPrecipitation", bool())));
  static final JsonSchema PLAYER_STATUS =
      schema(
          object(
              property("health", number(0.0, null)),
              property("maxHealth", number(0.0, null)),
              property("food", integer(0, null)),
              property("saturation", number(0.0, null)),
              property("experienceLevel", integer(0, null)),
              property("experienceProgress", number(0.0, 1.0)),
              property("position", POSITION),
              property("dimension", registryId()),
              property("gameMode", enumString("survival", "creative", "adventure", "spectator")),
              property("hardcore", bool()),
              property("armor", PLAYER_ARMOR),
              property("air", PLAYER_AIR),
              property(
                  "activeEffects", boundedArray(STATUS_EFFECT, 0, PlayerStatus.MAX_ACTIVE_EFFECTS)),
              property("activeEffectsTruncated", bool()),
              property("movement", PLAYER_MOVEMENT),
              property("conditions", PLAYER_CONDITIONS),
              property("selectedHotbarSlot", integer(0, InventorySnapshot.HOTBAR_SLOT_COUNT - 1)),
              property("attackCooldown", number(0.0, 1.0)),
              property("vehicle", nullable(PLAYER_VEHICLE)),
              property("respawn", nullable(PLAYER_RESPAWN))));
  static final JsonSchema INVENTORY =
      schema(
          object(
              property("selectedHotbarSlot", integer(0, InventorySnapshot.HOTBAR_SLOT_COUNT - 1)),
              property(
                  "slots", boundedArray(INVENTORY_SLOT, 0, InventorySnapshot.MAIN_SLOT_COUNT))));
  static final JsonSchema EQUIPMENT =
      schema(object(property("slots", boundedArray(EQUIPMENT_SLOT, 6, 6))));
  static final JsonSchema ADVANCEMENT_LIST_QUERY =
      schema(
          object(
              optionalProperty("filter", advancementFilter()),
              optionalProperty("search", string(1, AdvancementListQuery.MAX_SEARCH_LENGTH, null)),
              optionalProperty("limit", advancementLimit())));
  static final JsonSchema ADVANCEMENT_LIST_RESULT =
      schema(
          object(
              property("filter", enumString("ALL", "COMPLETED", "INCOMPLETE")),
              property("search", nullable(string(1, AdvancementListQuery.MAX_SEARCH_LENGTH, null))),
              property("limit", integer(1, AdvancementListQuery.MAX_LIMIT)),
              property("knownCount", integer(0, null)),
              property("scannedCount", integer(0, null)),
              property("matchedCount", integer(0, null)),
              property("sourceTruncated", bool()),
              property("truncated", bool()),
              property(
                  "advancements",
                  boundedArray(ADVANCEMENT_SUMMARY, 0, AdvancementListQuery.MAX_LIMIT))));
  static final JsonSchema ADVANCEMENT_LOOKUP_QUERY =
      schema(object(property("advancementId", registryId())));
  static final JsonSchema ADVANCEMENT_INFO = schema(ADVANCEMENT_DETAILS);
  static final JsonSchema TARGET_BLOCK = schema(BLOCK_INFO);
  static final JsonSchema TARGET_ENTITY = schema(ENTITY_INFO);
  static final JsonSchema NEARBY_CONTAINER_QUERY =
      schema(object(property("radius", number(0.0, null)), property("limit", integer(1, null))));
  static final JsonSchema NEARBY_CONTAINER_RESULT =
      schema(
          object(
              property("radius", number(0.0, null)),
              property("limit", integer(1, null)),
              property("truncated", bool()),
              property("containers", array(NEARBY_CONTAINER_SUMMARY))));
  static final JsonSchema CONTAINER_INSPECTION_QUERY =
      schema(object(property("position", BLOCK_POSITION)));
  static final JsonSchema CONTAINER_INSPECTION = schema(BLOCK_INFO);
  static final JsonSchema NEARBY_ENTITY_QUERY =
      schema(object(property("radius", number(0.0, null)), property("limit", integer(1, null))));
  static final JsonSchema NEARBY_ENTITY_RESULT =
      schema(
          object(
              property("radius", number(0.0, null)),
              property("limit", integer(1, null)),
              property("truncated", bool()),
              property("entities", array(ENTITY_INFO))));
  static final JsonSchema RECIPE_LOOKUP_QUERY =
      schema(object(property("itemId", string(1, 256, REGISTRY_ID_PATTERN))));
  static final JsonSchema CRAFTING_QUERY =
      schema(
          object(
              property("itemId", string(1, 256, REGISTRY_ID_PATTERN)),
              optionalProperty("scope", craftingScope())));
  static final JsonSchema RECIPE_LOOKUP_RESULT =
      schema(object(property("itemId", registryId()), property("recipes", array(RECIPE_INFO))));
  static final JsonSchema CRAFTING_RESULT =
      schema(
          object(
              property("itemId", registryId()),
              property("scope", enumString("PLAYER_ONLY", "PLAYER_AND_NEARBY")),
              property("sourceStatus", CRAFTING_SOURCE_STATUS),
              property("craftable", bool()),
              property("recipes", array(RECIPE_CRAFTABILITY))));
  static final JsonSchema CRAFTING_PLAN =
      schema(
          object(
              property("itemId", registryId()),
              property("scope", enumString("PLAYER_ONLY", "PLAYER_AND_NEARBY")),
              property("sourceStatus", CRAFTING_SOURCE_STATUS),
              property("requested", integer(1, null)),
              property("satisfiedFromInventory", integer(0, null)),
              property("satisfiedFromSources", array(FOUND_ITEM_SOURCE)),
              property("craftable", bool()),
              property("maxDepth", integer(1, null)),
              property("steps", boundedArray(CRAFTING_PLAN_STEP, 0, 512)),
              property("missingMaterials", array(MISSING_MATERIAL)),
              property("issues", array(CRAFTING_PLAN_ISSUE))));
  static final JsonSchema ITEM_SEARCH_QUERY =
      schema(object(property("query", string(1, 128, null)), property("limit", integer(1, null))));
  static final JsonSchema ITEM_SEARCH_RESULT =
      schema(
          object(
              property("query", string(1, 128, null)),
              property("limit", integer(1, null)),
              property("truncated", bool()),
              property("items", array(ITEM_INFO))));
  static final JsonSchema FIND_ITEM_QUERY =
      schema(
          object(
              property("query", string(1, 128, null)),
              property("radius", number(0.0, null)),
              property("containerLimit", integer(1, null)),
              property("itemLimit", integer(1, null))));
  static final JsonSchema FIND_ITEM_RESULT =
      schema(
          object(
              property("query", string(1, 128, null)),
              property("radius", number(0.0, null)),
              property("containerLimit", integer(1, null)),
              property("itemLimit", integer(1, ItemFinder.MAX_ITEM_RESULTS)),
              property("containersTruncated", bool()),
              property("itemsTruncated", bool()),
              property("matches", boundedArray(FOUND_ITEM, 0, ItemFinder.MAX_ITEM_RESULTS))));
  static final JsonSchema CAPABILITIES =
      schema(
          object(
              property("threadVersion", string(1, 128, null)),
              property("readOnly", bool()),
              property("tools", array(string(1, 128, TOOL_ID_PATTERN))),
              property("integrations", array(INTEGRATION_CAPABILITY))));

  private VanillaToolSchemas() {}

  private static JsonSchema schema(JsonObject document) {
    return JsonSchema.of(document);
  }

  private static JsonObject object(Property... properties) {
    JsonObject schema = typed("object");
    JsonObject propertySchemas = new JsonObject();
    JsonArray required = new JsonArray();
    for (Property property : properties) {
      propertySchemas.add(property.name(), property.schema().deepCopy());
      if (property.required()) {
        required.add(property.name());
      }
    }
    schema.add("properties", propertySchemas);
    schema.add("required", required);
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  private static JsonObject openObject() {
    JsonObject schema = typed("object");
    schema.addProperty("additionalProperties", true);
    return schema;
  }

  private static JsonObject array(JsonObject items) {
    JsonObject schema = typed("array");
    schema.add("items", items.deepCopy());
    return schema;
  }

  private static JsonObject boundedArray(JsonObject items, int minimum, int maximum) {
    JsonObject schema = array(items);
    schema.addProperty("minItems", minimum);
    schema.addProperty("maxItems", maximum);
    return schema;
  }

  private static JsonObject string(int minLength, Integer maxLength, String pattern) {
    JsonObject schema = typed("string");
    schema.addProperty("minLength", minLength);
    if (maxLength != null) {
      schema.addProperty("maxLength", maxLength);
    }
    if (pattern != null) {
      schema.addProperty("pattern", pattern);
    }
    return schema;
  }

  private static JsonObject registryId() {
    return string(1, 256, REGISTRY_ID_PATTERN);
  }

  private static JsonObject enumString(String... values) {
    JsonObject schema = typed("string");
    JsonArray allowed = new JsonArray();
    for (String value : values) {
      allowed.add(value);
    }
    schema.add("enum", allowed);
    return schema;
  }

  private static JsonObject nullableEnumString(String... values) {
    JsonObject schema = enumString(values);
    JsonArray types = new JsonArray();
    types.add("string");
    types.add("null");
    schema.add("type", types);
    schema.getAsJsonArray("enum").add(com.google.gson.JsonNull.INSTANCE);
    return schema;
  }

  private static JsonObject number() {
    return typed("number");
  }

  private static JsonObject number(Double minimum, Double maximum) {
    JsonObject schema = number();
    if (minimum != null) {
      schema.addProperty("minimum", minimum);
    }
    if (maximum != null) {
      schema.addProperty("maximum", maximum);
    }
    return schema;
  }

  private static JsonObject integer() {
    return typed("integer");
  }

  private static JsonObject integer(Integer minimum, Integer maximum) {
    JsonObject schema = integer();
    if (minimum != null) {
      schema.addProperty("minimum", minimum);
    }
    if (maximum != null) {
      schema.addProperty("maximum", maximum);
    }
    return schema;
  }

  private static JsonObject bool() {
    return typed("boolean");
  }

  private static JsonObject craftingScope() {
    JsonObject schema = enumString("PLAYER_ONLY", "PLAYER_AND_NEARBY");
    schema.addProperty("default", "PLAYER_ONLY");
    schema.addProperty(
        "description",
        "PLAYER_ONLY uses the 36-slot main inventory and is the default. "
            + "PLAYER_AND_NEARBY explicitly adds eligible nearby loaded containers within "
            + "Thread's configured bounds; results report incomplete or truncated discovery.");
    return schema;
  }

  private static JsonObject advancementFilter() {
    JsonObject schema = enumString("ALL", "COMPLETED", "INCOMPLETE");
    schema.addProperty("default", "ALL");
    schema.addProperty(
        "description",
        "ALL returns every advancement currently known to the player. COMPLETED and INCOMPLETE "
            + "filter by live integrated-server progress.");
    return schema;
  }

  private static JsonObject advancementLimit() {
    JsonObject schema = integer(1, AdvancementListQuery.MAX_LIMIT);
    schema.addProperty("default", AdvancementListQuery.DEFAULT_LIMIT);
    schema.addProperty(
        "description",
        "Maximum returned advancements. Results include truncation metadata when this bound or "
            + "the provider scan ceiling omits entries.");
    return schema;
  }

  private static JsonObject clientOptionsSections() {
    JsonObject schema =
        boundedArray(CLIENT_OPTIONS_SECTION, 1, ClientOptionsSection.values().length);
    JsonArray defaults = new JsonArray();
    ClientOptionsQuery.defaultSections().forEach(section -> defaults.add(section.name()));
    schema.add("default", defaults);
    schema.addProperty(
        "description",
        "Sections to return. The bounded default excludes KEYBINDS; request KEYBINDS explicitly "
            + "when bindings are needed.");
    return schema;
  }

  private static JsonObject clientOptionsKeybindLimit() {
    JsonObject schema = integer(1, ClientOptionsQuery.MAX_KEYBIND_LIMIT);
    schema.addProperty("default", ClientOptionsQuery.DEFAULT_KEYBIND_LIMIT);
    schema.addProperty(
        "description",
        "Maximum keybind records returned when KEYBINDS is selected. The response reports total, "
            + "returned, and truncation metadata.");
    return schema;
  }

  private static JsonObject nullable(JsonObject requiredSchema) {
    JsonObject schema = requiredSchema.deepCopy();
    String declaredType = schema.get("type").getAsString();
    JsonArray types = new JsonArray();
    types.add(declaredType);
    types.add("null");
    schema.add("type", types);
    return schema;
  }

  private static JsonObject typed(String type) {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", type);
    return schema;
  }

  private static Property property(String name, JsonObject schema) {
    return new Property(name, schema, true);
  }

  private static Property optionalProperty(String name, JsonObject schema) {
    return new Property(name, schema, false);
  }

  private record Property(String name, JsonObject schema, boolean required) {}
}
