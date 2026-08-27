package me.clutchy.thread.platform.fabric;

import static me.clutchy.thread.gametest.LoaderParityAssertions.recipeOccurrences;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.gametest.ExternalProofIntegration;
import me.clutchy.thread.gametest.LoaderParityAssertions;
import me.clutchy.thread.runtime.ThreadRuntime;
import me.clutchy.thread.transport.mcp.McpHttpServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** End-to-end proof of native gameplay reads and the external-mod integration bridge. */
@SuppressWarnings("UnstableApiUsage")
public final class FabricProviderClientGameTest implements FabricClientGameTest {
  private static final String NATIVE_COMMAND_BLOCK_RECIPE =
      "thread:native_command_block_from_earth";
  private static final String NATIVE_CHAIN_COMMAND_BLOCK_RECIPE =
      "thread:native_chain_command_block_from_command_block";

  @Override
  public void runTest(ClientGameTestContext context) {
    ThreadFabricClient entrypoint =
        FabricLoader.getInstance()
            .getEntrypointContainers("client", ClientModInitializer.class)
            .stream()
            .filter(container -> container.getProvider().getMetadata().getId().equals("thread"))
            .map(container -> (ThreadFabricClient) container.getEntrypoint())
            .findFirst()
            .orElseThrow();
    ThreadRuntime runtime = entrypoint.runtime();
    ToolRegistry tools = runtime.tools();
    LoaderParityAssertions.verifyConfiguration(
        FabricLoader.getInstance().getConfigDir().resolve("thread.json"));
    if (Boolean.getBoolean(LoaderParityAssertions.EXPECT_MCP_DISABLED)) {
      LoaderParityAssertions.verifyDisabledRuntime(runtime, "external-loader-discovery");
      return;
    }
    awaitExternal(
        context,
        () -> {
          LoaderParityAssertions.verifyMenu(runtime, "fabric", "external-loader-discovery");
          return null;
        });
    McpHttpServer mcp = runtime.mcpServer();

    try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
      singleplayer.getClientLevel().waitForChunksDownload();
      singleplayer.getServer().runCommand("fill -3 99 -3 3 99 5 minecraft:stone");
      singleplayer
          .getServer()
          .runCommand("setblock 0 101 3 minecraft:furnace[facing=north,lit=false]");
      singleplayer
          .getServer()
          .runCommand("item replace block 0 101 3 container.0 with minecraft:iron_ore 3");
      singleplayer
          .getServer()
          .runCommand("item replace block 0 101 3 container.1 with minecraft:coal");
      singleplayer.getServer().runCommand("setblock -2 101 2 minecraft:barrel");
      singleplayer.getServer().runCommand("setblock -3 101 2 minecraft:chest");
      singleplayer
          .getServer()
          .runCommand("item replace block -3 101 2 container.0 with minecraft:coal");
      singleplayer.getServer().runCommand("setblock 2 101 2 minecraft:hopper");
      for (int slot = 0; slot < 5; slot++) {
        singleplayer
            .getServer()
            .runCommand(
                "item replace block 2 101 2 container." + slot + " with minecraft:stone 64");
      }
      singleplayer.getServer().runCommand("tp @a 0.5 100 0.5 0 0");
      singleplayer
          .getServer()
          .runCommand("item replace entity @a hotbar.0 with minecraft:diamond_pickaxe");
      singleplayer.getServer().runCommand("enchant @a minecraft:efficiency 5");
      singleplayer
          .getServer()
          .runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
      singleplayer.getServer().runCommand("give @a minecraft:diamond 3");
      singleplayer.getServer().runCommand("give @a minecraft:stick 2");
      singleplayer.getServer().runCommand("give @a minecraft:oak_log 1");
      singleplayer.getServer().runCommand("give @a minecraft:dirt 1");
      singleplayer.getServer().runCommand("give @a minecraft:coal 2");
      singleplayer.getServer().runCommand("effect give @a minecraft:speed 60 1 true");
      singleplayer.getServer().runCommand("spawnpoint @a 1 100 1");
      singleplayer
          .getServer()
          .runCommand("summon minecraft:minecart -2 100 0 {CustomName:'Thread Ride'}");
      singleplayer
          .getServer()
          .runCommand(
              "summon minecraft:zombie 3 100 0 "
                  + "{NoAI:1b,Silent:1b,Invulnerable:1b,IsBaby:1b,"
                  + "CustomName:'Thread Target'}");
      singleplayer
          .getServer()
          .runCommand(
              "item replace entity @e[type=minecraft:zombie,limit=1,sort=nearest] "
                  + "weapon.mainhand with minecraft:iron_sword");
      singleplayer
          .getServer()
          .runCommand(
              "item replace entity @e[type=minecraft:zombie,limit=1,sort=nearest] "
                  + "armor.head with minecraft:iron_helmet");
      singleplayer
          .getServer()
          .runCommand(
              "effect give @e[type=minecraft:zombie,limit=1,sort=nearest] "
                  + "minecraft:speed 60 1 true");
      singleplayer.getServer().runCommand("time set day");
      singleplayer.getServer().runCommand("weather clear");
      // Waiting for the command's observable client state keeps this packaged test deterministic
      // without depending on a game-test packet-drain convenience API.
      context.waitFor(
          client ->
              hasInventoryStack(client, Items.DIAMOND, 3)
                  && hasInventoryStack(client, Items.STICK, 2)
                  && hasInventoryStack(client, Items.OAK_LOG, 1)
                  && hasInventoryStack(client, Items.DIRT, 1)
                  && hasInventoryStack(client, Items.COAL, 2)
                  && client.player != null
                  && client.player.getMainHandItem().is(Items.DIAMOND_PICKAXE));
      context.waitFor(FabricProviderClientGameTest::targetsKnownFurnace);

      awaitExternal(
          context,
          () -> {
            LoaderParityAssertions.verifyWorld(runtime, true);
            return null;
          });

      verifyWorldEnvironmentTransitions(context, singleplayer, tools);

      assertTrue(
          invokeSuccessfully(context, tools, "minecraft.get_status", "{}")
              .get("supported")
              .getAsBoolean(),
          "single-player support");

      JsonObject unknownItem = new JsonObject();
      unknownItem.addProperty("itemId", "thread:missing_item");
      assertToolError(
          mcpToolResult(context, mcp.endpoint(), 21, "minecraft.get_recipe", unknownItem),
          "NOT_FOUND",
          "The requested item is not registered.",
          false,
          "unknown recipe item rejection");

      JsonObject player = invokeSuccessfully(context, tools, "minecraft.get_player", "{}");
      assertEquals(
          "minecraft:overworld", player.get("dimension").getAsString(), "player dimension");
      verifyRichPlayerStatus(player);
      verifyPlayerStateTransitions(context, singleplayer, tools);

      JsonObject recipes =
          invokeSuccessfully(
              context, tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
      assertTrue(!recipes.getAsJsonArray("recipes").isEmpty(), "diamond pickaxe recipe present");
      assertEquals(1, recipeOccurrences(recipes, "minecraft:dirt"), "datapack override dirt input");
      assertEquals(0, recipeOccurrences(recipes, "minecraft:diamond"), "replaced vanilla diamonds");
      assertEquals(0, recipeOccurrences(recipes, "minecraft:stick"), "replaced vanilla sticks");
      JsonObject directCraftability =
          invokeSuccessfully(
              context, tools, "minecraft.can_craft", "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
      assertTrue(directCraftability.get("craftable").getAsBoolean(), "direct craftability");
      JsonObject directMissing =
          invokeSuccessfully(
              context,
              tools,
              "minecraft.get_missing_ingredients",
              "{\"itemId\":\"minecraft:diamond_pickaxe\"}");
      assertTrue(directMissing.get("craftable").getAsBoolean(), "direct missing ingredient counts");
      JsonObject directPlan =
          invokeSuccessfully(
              context,
              tools,
              "minecraft.get_crafting_plan",
              "{\"itemId\":\"minecraft:crafting_table\"}");
      assertTrue(directPlan.get("craftable").getAsBoolean(), "direct recursive crafting plan");
      assertTrue(directPlan.getAsJsonArray("steps").size() >= 2, "recursive intermediate steps");
      assertEquals(
          "minecraft:crafting_table",
          directPlan
              .getAsJsonArray("steps")
              .get(directPlan.getAsJsonArray("steps").size() - 1)
              .getAsJsonObject()
              .get("itemId")
              .getAsString(),
          "recursive plan final step");
      assertTrue(directPlan.getAsJsonArray("missingMaterials").isEmpty(), "no raw shortages");
      assertTrue(directPlan.getAsJsonArray("issues").isEmpty(), "no planning safety issues");

      verifyNativeRecipes(context, tools);
      assertTrue(
          ExternalProofIntegration.recipeLookups() > 0,
          "external recipe contribution receives live lookups");
      JsonObject nativeOnlyRecipes =
          invokeSuccessfully(
              context, tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:barrier\"}");
      assertTrue(
          nativeOnlyRecipes.getAsJsonArray("recipes").isEmpty(),
          "native provider does not invent recipes absent from the live manager");

      JsonObject search =
          invokeSuccessfully(
              context,
              tools,
              "minecraft.search_items",
              "{\"query\":\"diamond pick\",\"limit\":10}");
      assertTrue(
          search.getAsJsonArray("items").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(
                  item -> item.get("itemId").getAsString().equals("minecraft:diamond_pickaxe")),
          "live item registry search");

      JsonObject directEquipment =
          invokeSuccessfully(context, tools, "minecraft.get_equipment", "{}");
      assertEquals(6, directEquipment.getAsJsonArray("slots").size(), "fixed equipment slot count");
      ToolResult<JsonElement> target = invoke(context, tools, "minecraft.get_target_block", "{}");
      assertTrue(target.successful(), "target-block result");
      invokeSuccessfully(
          context, tools, "minecraft.get_nearby_entities", "{\"radius\":16,\"limit\":8}");
      JsonObject directContainers =
          invokeSuccessfully(
              context, tools, "minecraft.get_nearby_containers", "{\"radius\":16,\"limit\":8}");
      assertTrue(
          directContainers.getAsJsonArray("containers").size() >= 3, "nearby container discovery");
      assertDistanceOrdered(directContainers);
      assertEquals(
          0,
          container(directContainers, "minecraft:barrel").get("usedSlotCount").getAsInt(),
          "empty barrel occupancy");
      JsonObject hopperSummary = container(directContainers, "minecraft:hopper");
      assertEquals(5, hopperSummary.get("usedSlotCount").getAsInt(), "full hopper occupancy");
      assertEquals(4, hopperSummary.getAsJsonArray("itemSummary").size(), "short item summary");
      assertTrue(hopperSummary.get("itemSummaryTruncated").getAsBoolean(), "summary truncation");

      JsonObject directFurnace =
          invokeSuccessfully(
              context,
              tools,
              "minecraft.inspect_container",
              "{\"position\":{\"x\":0,\"y\":101,\"z\":3}}");
      JsonObject directFurnaceEntity = directFurnace.getAsJsonObject("blockEntity");
      assertEquals(3, directFurnaceEntity.get("inventorySize").getAsInt(), "furnace slots");
      assertEquals(
          "minecraft:iron_ore",
          blockEntityItem(directFurnaceEntity, "input").get("itemId").getAsString(),
          "inspected furnace input");
      assertTrue(
          directFurnaceEntity.getAsJsonObject("state").has("cookingProgress"),
          "furnace cooking progress");
      JsonObject directBarrel =
          invokeSuccessfully(
              context,
              tools,
              "minecraft.inspect_container",
              "{\"position\":{\"x\":-2,\"y\":101,\"z\":2}}");
      assertEquals(
          27,
          directBarrel.getAsJsonObject("blockEntity").get("inventorySize").getAsInt(),
          "empty barrel slots");
      assertTrue(
          directBarrel.getAsJsonObject("blockEntity").getAsJsonArray("items").isEmpty(),
          "empty barrel inventory");
      JsonObject directHopper =
          invokeSuccessfully(
              context,
              tools,
              "minecraft.inspect_container",
              "{\"position\":{\"x\":2,\"y\":101,\"z\":2}}");
      assertEquals(
          5,
          directHopper.getAsJsonObject("blockEntity").getAsJsonArray("items").size(),
          "full hopper inventory");

      assertEquals(
          "OUT_OF_RANGE",
          invoke(context, tools, "minecraft.get_nearby_containers", "{\"radius\":17,\"limit\":8}")
              .error()
              .code()
              .name(),
          "container radius limit");
      assertEquals(
          "RESULT_LIMIT_EXCEEDED",
          invoke(context, tools, "minecraft.get_nearby_containers", "{\"radius\":8,\"limit\":65}")
              .error()
              .code()
              .name(),
          "container result limit");
      assertEquals(
          "OUT_OF_RANGE",
          invoke(
                  context,
                  tools,
                  "minecraft.inspect_container",
                  "{\"position\":{\"x\":32,\"y\":100,\"z\":0}}")
              .error()
              .code()
              .name(),
          "container inspection range");
      assertEquals(
          "NOT_FOUND",
          invoke(
                  context,
                  tools,
                  "minecraft.inspect_container",
                  "{\"position\":{\"x\":0,\"y\":99,\"z\":0}}")
              .error()
              .code()
              .name(),
          "non-container inspection");

      JsonObject inventory = invokeSuccessfully(context, tools, "minecraft.get_inventory", "{}");
      assertTrue(
          inventory.getAsJsonArray("slots").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .map(slot -> slot.getAsJsonObject("stack"))
              .anyMatch(
                  stack ->
                      stack.get("itemId").getAsString().equals("minecraft:diamond")
                          && stack.get("count").getAsInt() == 3),
          "inventory preserves registry ID and count");

      JsonObject mcpStatus =
          mcpTool(context, mcp.endpoint(), 6, "minecraft.get_status", new JsonObject());
      assertTrue(mcpStatus.get("supported").getAsBoolean(), "MCP loaded-world support");
      assertEquals("SINGLEPLAYER", mcpStatus.get("state").getAsString(), "MCP single-player state");

      JsonObject mcpPlayer =
          mcpTool(context, mcp.endpoint(), 7, "minecraft.get_player", new JsonObject());
      assertEquals(
          "minecraft:overworld", mcpPlayer.get("dimension").getAsString(), "MCP player dimension");
      assertTrue(mcpPlayer.get("health").getAsDouble() > 0, "MCP health");
      assertTrue(mcpPlayer.get("food").getAsInt() > 0, "MCP hunger/food level");

      JsonObject mcpInventory =
          mcpTool(context, mcp.endpoint(), 8, "minecraft.get_inventory", new JsonObject());
      assertEquals(3, itemCount(mcpInventory, "minecraft:diamond"), "MCP diamond count");
      assertEquals(2, itemCount(mcpInventory, "minecraft:stick"), "MCP stick count");
      assertEquals(
          0,
          itemCount(mcpInventory, "minecraft:diamond_helmet"),
          "inventory excludes equipment slots");

      JsonObject mcpEquipment =
          mcpTool(context, mcp.endpoint(), 9, "minecraft.get_equipment", new JsonObject());
      JsonObject mainHand = equipmentItem(mcpEquipment, "MAIN_HAND");
      assertEquals(
          "minecraft:diamond_pickaxe",
          mainHand.get("itemId").getAsString(),
          "MCP main-hand equipment");
      assertTrue(
          mainHand.getAsJsonObject("durability").get("maximum").getAsInt() > 0, "durability");
      assertTrue(!mainHand.getAsJsonArray("enchantments").isEmpty(), "enchantments");
      assertEquals(
          "minecraft:diamond_helmet",
          equipmentItem(mcpEquipment, "HEAD").get("itemId").getAsString(),
          "MCP head equipment");
      assertTrue(
          equipmentSlot(mcpEquipment, "OFF_HAND").get("item").isJsonNull(), "empty off hand");

      JsonObject mcpTarget =
          mcpTool(context, mcp.endpoint(), 10, "minecraft.get_target_block", new JsonObject());
      assertEquals(
          "minecraft:furnace", mcpTarget.get("blockId").getAsString(), "MCP targeted block");
      assertEquals("Furnace", mcpTarget.get("displayName").getAsString(), "block display name");
      assertEquals(
          "north",
          mcpTarget.getAsJsonObject("properties").get("facing").getAsString(),
          "block state property");
      assertTrue(mcpTarget.get("blockEntityPresent").getAsBoolean(), "block entity presence");
      JsonObject furnace = mcpTarget.getAsJsonObject("blockEntity");
      assertEquals("minecraft:furnace", furnace.get("typeId").getAsString(), "block entity type");
      assertEquals(
          "minecraft:iron_ore",
          blockEntityItem(furnace, "input").get("itemId").getAsString(),
          "furnace input item");

      JsonObject entityArguments = new JsonObject();
      entityArguments.addProperty("radius", 16);
      entityArguments.addProperty("limit", 8);
      JsonObject mcpEntities =
          mcpTool(context, mcp.endpoint(), 11, "minecraft.get_nearby_entities", entityArguments);
      assertTrue(
          mcpEntities.getAsJsonArray("entities").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(
                  entity -> entity.get("entityType").getAsString().equals("minecraft:minecart")),
          "MCP nearby entity within 16 blocks");
      JsonObject zombie = entity(mcpEntities, "minecraft:zombie");
      assertEquals("Zombie", zombie.get("displayName").getAsString(), "entity display name");
      assertTrue(zombie.get("living").getAsBoolean(), "living entity marker");
      assertTrue(zombie.get("health").getAsDouble() > 0, "living entity health");
      assertEquals("HOSTILE", zombie.get("classification").getAsString(), "hostile classification");
      assertTrue(zombie.get("distance").getAsDouble() > 0, "entity distance");
      JsonObject minecart = entity(mcpEntities, "minecraft:minecart");
      assertTrue(!minecart.get("living").getAsBoolean(), "non-living entity marker");
      assertTrue(minecart.get("health").isJsonNull(), "non-living health absence");

      verifyTargetEntityInspection(context, singleplayer, tools, mcp);

      JsonObject containerArguments = new JsonObject();
      containerArguments.addProperty("radius", 16);
      containerArguments.addProperty("limit", 8);
      JsonObject mcpContainers =
          mcpTool(
              context, mcp.endpoint(), 40, "minecraft.get_nearby_containers", containerArguments);
      assertTrue(
          mcpContainers.getAsJsonArray("containers").size() >= 3, "MCP nearby container discovery");
      JsonObject furnacePosition = new JsonObject();
      furnacePosition.addProperty("x", 0);
      furnacePosition.addProperty("y", 101);
      furnacePosition.addProperty("z", 3);
      JsonObject inspectionArguments = new JsonObject();
      inspectionArguments.add("position", furnacePosition);
      JsonObject mcpFurnace =
          mcpTool(context, mcp.endpoint(), 41, "minecraft.inspect_container", inspectionArguments);
      assertEquals(
          "minecraft:furnace", mcpFurnace.get("blockId").getAsString(), "MCP inspected furnace");

      JsonObject findCoalArguments = new JsonObject();
      findCoalArguments.addProperty("query", "minecraft:coal");
      findCoalArguments.addProperty("radius", 16);
      findCoalArguments.addProperty("containerLimit", 8);
      findCoalArguments.addProperty("itemLimit", 16);
      JsonObject foundCoal =
          mcpTool(context, mcp.endpoint(), 42, "minecraft.find_item", findCoalArguments);
      JsonObject coal = foundCoal.getAsJsonArray("matches").get(0).getAsJsonObject();
      assertEquals(
          "minecraft:coal",
          coal.getAsJsonObject("item").get("itemId").getAsString(),
          "found item ID");
      assertEquals(3, coal.get("totalCount").getAsInt(), "player and furnace coal total");
      assertEquals(2, coal.getAsJsonArray("sources").size(), "coal source count");
      assertEquals(
          "PLAYER_INVENTORY",
          coal.getAsJsonArray("sources").get(0).getAsJsonObject().get("sourceType").getAsString(),
          "player source first");
      JsonObject chestCoalSource = coal.getAsJsonArray("sources").get(1).getAsJsonObject();
      assertEquals(
          "NEARBY_CONTAINER",
          chestCoalSource.get("sourceType").getAsString(),
          "container source second");
      assertEquals(
          "minecraft:chest",
          chestCoalSource.get("containerTypeId").getAsString(),
          "container source type");
      assertEquals(
          -3,
          chestCoalSource.getAsJsonObject("containerPosition").get("x").getAsInt(),
          "container source position");

      JsonObject findStoneArguments = findCoalArguments.deepCopy();
      findStoneArguments.addProperty("query", "stone");
      JsonObject foundStone =
          mcpTool(context, mcp.endpoint(), 43, "minecraft.find_item", findStoneArguments);
      JsonObject stone = foundStone.getAsJsonArray("matches").get(0).getAsJsonObject();
      assertEquals(
          "minecraft:stone",
          stone.getAsJsonObject("item").get("itemId").getAsString(),
          "text item match");
      assertEquals(320, stone.get("totalCount").getAsInt(), "duplicate hopper stacks aggregate");
      assertEquals(1, stone.getAsJsonArray("sources").size(), "hopper is one aggregated source");

      JsonObject recipeArguments = new JsonObject();
      recipeArguments.addProperty("itemId", "minecraft:diamond_pickaxe");
      JsonObject mcpRecipe =
          mcpTool(context, mcp.endpoint(), 12, "minecraft.get_recipe", recipeArguments);
      assertTrue(!mcpRecipe.getAsJsonArray("recipes").isEmpty(), "MCP diamond pickaxe recipe");
      assertTrue(
          recipeOccurrences(mcpRecipe, "minecraft:dirt") == 1,
          "MCP recipe sees the datapack override");
      assertTrue(
          itemCount(mcpInventory, "minecraft:dirt") >= 1,
          "MCP inventory has the overridden recipe material");

      JsonObject mcpCraftability =
          mcpTool(context, mcp.endpoint(), 13, "minecraft.can_craft", recipeArguments);
      assertTrue(mcpCraftability.get("craftable").getAsBoolean(), "MCP craftability");
      assertTrue(
          mcpCraftability.getAsJsonArray("recipes").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(recipe -> recipe.get("craftable").getAsBoolean()),
          "MCP identifies a craftable recipe variant");

      JsonObject mcpMissing =
          mcpTool(
              context, mcp.endpoint(), 14, "minecraft.get_missing_ingredients", recipeArguments);
      assertTrue(mcpMissing.get("craftable").getAsBoolean(), "MCP missing ingredient counts");

      JsonObject planArguments = new JsonObject();
      planArguments.addProperty("itemId", "minecraft:crafting_table");
      JsonObject mcpPlan =
          mcpTool(context, mcp.endpoint(), 15, "minecraft.get_crafting_plan", planArguments);
      assertTrue(mcpPlan.get("craftable").getAsBoolean(), "MCP recursive crafting plan");
      assertTrue(mcpPlan.getAsJsonArray("steps").size() >= 2, "MCP recursive plan steps");
      assertTrue(mcpPlan.getAsJsonArray("missingMaterials").isEmpty(), "MCP plan raw shortages");
      assertTrue(mcpPlan.getAsJsonArray("issues").isEmpty(), "MCP plan safety issues");

      singleplayer.getServer().runCommand("clear @a minecraft:dirt");
      context.waitFor(client -> !hasInventoryItem(client, Items.DIRT));
      JsonObject storageRecipeArguments = new JsonObject();
      storageRecipeArguments.addProperty("itemId", "minecraft:command_block");
      JsonObject playerOnlyStorageCraftability =
          mcpTool(context, mcp.endpoint(), 44, "minecraft.can_craft", storageRecipeArguments);
      assertEquals(
          "PLAYER_ONLY",
          playerOnlyStorageCraftability.get("scope").getAsString(),
          "storage crafting default scope");
      assertTrue(
          !playerOnlyStorageCraftability.get("craftable").getAsBoolean(),
          "nearby stone does not affect default crafting");

      JsonObject expandedStorageArguments = storageRecipeArguments.deepCopy();
      expandedStorageArguments.addProperty("scope", "PLAYER_AND_NEARBY");
      JsonObject expandedStorageCraftability =
          mcpTool(context, mcp.endpoint(), 45, "minecraft.can_craft", expandedStorageArguments);
      assertEquals(
          "PLAYER_AND_NEARBY",
          expandedStorageCraftability.get("scope").getAsString(),
          "storage crafting expanded scope");
      JsonObject storageStatus = expandedStorageCraftability.getAsJsonObject("sourceStatus");
      assertTrue(storageStatus.get("complete").getAsBoolean(), "storage source snapshot complete");
      assertEquals(16D, storageStatus.get("nearbyRadius").getAsDouble(), "storage radius bound");
      assertEquals(
          64, storageStatus.get("nearbyContainerLimit").getAsInt(), "storage container bound");
      assertTrue(
          expandedStorageCraftability.get("craftable").getAsBoolean(),
          "nearby hopper stone enables expanded crafting");
      JsonObject storageAllocation =
          recipe(expandedStorageCraftability, NATIVE_COMMAND_BLOCK_RECIPE)
              .getAsJsonArray("ingredients")
              .get(0)
              .getAsJsonObject()
              .getAsJsonArray("allocations")
              .get(0)
              .getAsJsonObject();
      assertEquals(
          "minecraft:stone",
          storageAllocation.get("itemId").getAsString(),
          "expanded crafting selected nearby alternative");
      JsonObject storageSource =
          storageAllocation.getAsJsonArray("sourceAllocations").get(0).getAsJsonObject();
      assertEquals(
          "NEARBY_CONTAINER",
          storageSource.get("sourceType").getAsString(),
          "expanded crafting source type");
      assertEquals(
          "minecraft:hopper",
          storageSource.get("containerTypeId").getAsString(),
          "expanded crafting source container");
      assertEquals(
          2,
          storageSource.getAsJsonObject("containerPosition").get("x").getAsInt(),
          "expanded crafting source position");

      JsonObject expandedStorageMissing =
          mcpTool(
              context,
              mcp.endpoint(),
              46,
              "minecraft.get_missing_ingredients",
              expandedStorageArguments);
      assertTrue(
          expandedStorageMissing.get("craftable").getAsBoolean(),
          "expanded missing ingredients uses nearby storage");
      JsonObject expandedPlanArguments = new JsonObject();
      expandedPlanArguments.addProperty("itemId", "minecraft:chain_command_block");
      expandedPlanArguments.addProperty("scope", "PLAYER_AND_NEARBY");
      JsonObject expandedStoragePlan =
          mcpTool(
              context, mcp.endpoint(), 47, "minecraft.get_crafting_plan", expandedPlanArguments);
      assertTrue(
          expandedStoragePlan.get("craftable").getAsBoolean(),
          "expanded recursive plan uses nearby storage");
      JsonObject intermediateStorageStep =
          expandedStoragePlan.getAsJsonArray("steps").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .filter(
                  step -> step.get("recipeId").getAsString().equals(NATIVE_COMMAND_BLOCK_RECIPE))
              .findFirst()
              .orElseThrow();
      JsonObject plannedStorageSource =
          intermediateStorageStep
              .getAsJsonArray("ingredients")
              .get(0)
              .getAsJsonObject()
              .getAsJsonArray("allocations")
              .get(0)
              .getAsJsonObject()
              .getAsJsonArray("sourceAllocations")
              .get(0)
              .getAsJsonObject();
      assertEquals(
          "minecraft:hopper",
          plannedStorageSource.get("containerTypeId").getAsString(),
          "expanded recursive plan source container");

      JsonObject searchArguments = new JsonObject();
      searchArguments.addProperty("query", "diamond pick");
      searchArguments.addProperty("limit", 10);
      JsonObject mcpSearch =
          mcpTool(context, mcp.endpoint(), 16, "minecraft.search_items", searchArguments);
      assertTrue(
          mcpSearch.getAsJsonArray("items").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(
                  item -> item.get("itemId").getAsString().equals("minecraft:diamond_pickaxe")),
          "MCP item search");

      JsonObject mcpCapabilities =
          mcpTool(context, mcp.endpoint(), 17, "minecraft.get_capabilities", new JsonObject());
      assertEquals(20, mcpCapabilities.getAsJsonArray("tools").size(), "MCP capability tool count");
      assertEquals(
          2, mcpCapabilities.getAsJsonArray("integrations").size(), "MCP integration count");
      assertTrue(
          mcpCapabilities.getAsJsonArray("integrations").asList().stream()
              .map(JsonElement::getAsJsonObject)
              .anyMatch(
                  integration -> integration.get("id").getAsString().equals("gametest-bridge")),
          "MCP external integration identity");
    }

    context.waitFor(
        client ->
            client.level == null
                && client.player == null
                && client.getSingleplayerServer() == null);
    awaitExternal(
        context,
        () -> {
          LoaderParityAssertions.verifyReturnedToMenu(runtime);
          return null;
        });
  }

  private static void verifyNativeRecipes(ClientGameTestContext context, ToolRegistry tools) {
    JsonObject recipes =
        invokeSuccessfully(
            context, tools, "minecraft.get_recipe", "{\"itemId\":\"minecraft:command_block\"}");
    JsonObject nativeRecipe = recipe(recipes, NATIVE_COMMAND_BLOCK_RECIPE);
    JsonObject nativeIngredient =
        nativeRecipe.getAsJsonArray("ingredients").get(0).getAsJsonObject();
    assertEquals(
        2, nativeIngredient.getAsJsonArray("itemIds").size(), "native recipe alternatives");
    assertEquals(1, nativeIngredient.get("count").getAsInt(), "native recipe input count");
    assertEquals(1, recipeOccurrences(recipes, "minecraft:dirt"), "custom recipe dirt option");
    assertEquals(1, recipeOccurrences(recipes, "minecraft:stone"), "custom recipe stone option");

    JsonObject craftability =
        invokeSuccessfully(
            context, tools, "minecraft.can_craft", "{\"itemId\":\"minecraft:command_block\"}");
    assertTrue(craftability.get("craftable").getAsBoolean(), "native recipe craftability");
    JsonObject missing =
        invokeSuccessfully(
            context,
            tools,
            "minecraft.get_missing_ingredients",
            "{\"itemId\":\"minecraft:command_block\"}");
    assertTrue(missing.get("craftable").getAsBoolean(), "native recipe missing ingredients");
    JsonObject plan =
        invokeSuccessfully(
            context,
            tools,
            "minecraft.get_crafting_plan",
            "{\"itemId\":\"minecraft:chain_command_block\"}");
    assertTrue(plan.get("craftable").getAsBoolean(), "native recursive crafting plan");
    assertTrue(
        plan.getAsJsonArray("steps").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .map(step -> step.get("recipeId").getAsString())
            .anyMatch(NATIVE_COMMAND_BLOCK_RECIPE::equals),
        "native recursive intermediate recipe");
    assertTrue(
        plan.getAsJsonArray("steps").asList().stream()
            .map(JsonElement::getAsJsonObject)
            .map(step -> step.get("recipeId").getAsString())
            .anyMatch(NATIVE_CHAIN_COMMAND_BLOCK_RECIPE::equals),
        "native recursive final recipe");
    assertTrue(plan.getAsJsonArray("missingMaterials").isEmpty(), "native plan raw shortages");
    assertTrue(plan.getAsJsonArray("issues").isEmpty(), "native plan safety limits");
  }

  private static void verifyRichPlayerStatus(JsonObject player) {
    assertEquals("survival", player.get("gameMode").getAsString(), "player game mode");
    assertTrue(!player.get("hardcore").getAsBoolean(), "non-hardcore test world");
    assertTrue(player.getAsJsonObject("armor").get("value").getAsInt() > 0, "equipped armor value");
    assertTrue(
        player.getAsJsonObject("armor").get("toughness").getAsDouble() > 0,
        "equipped armor toughness");
    assertEquals(300, player.getAsJsonObject("air").get("current").getAsInt(), "current air");
    assertEquals(300, player.getAsJsonObject("air").get("maximum").getAsInt(), "maximum air");
    JsonObject speed = statusEffect(player, "minecraft:speed");
    assertEquals(1, speed.get("amplifier").getAsInt(), "player speed amplifier");
    assertTrue(speed.get("durationTicks").getAsInt() > 0, "player effect duration");
    assertTrue(!player.get("activeEffectsTruncated").getAsBoolean(), "player effects fit bound");
    assertEquals(0, player.get("selectedHotbarSlot").getAsInt(), "selected hotbar slot");
    assertTrue(
        player.get("attackCooldown").getAsDouble() >= 0
            && player.get("attackCooldown").getAsDouble() <= 1,
        "player attack cooldown");
    assertTrue(player.get("vehicle").isJsonNull(), "initial vehicle absence");
    JsonObject respawn = player.getAsJsonObject("respawn");
    assertEquals(
        "minecraft:overworld", respawn.get("dimension").getAsString(), "respawn dimension");
    assertEquals(1, respawn.getAsJsonObject("position").get("x").getAsInt(), "respawn x");
    assertEquals(100, respawn.getAsJsonObject("position").get("y").getAsInt(), "respawn y");
    assertEquals(1, respawn.getAsJsonObject("position").get("z").getAsInt(), "respawn z");
    assertTrue(respawn.get("forced").getAsBoolean(), "forced respawn point");
  }

  private static void verifyPlayerStateTransitions(
      ClientGameTestContext context, TestSingleplayerContext singleplayer, ToolRegistry tools) {
    singleplayer.getServer().runCommand("time set night");
    runOnServerAndWait(
        context,
        singleplayer,
        server -> {
          var player = server.getPlayerList().getPlayers().getFirst();
          player.setSprinting(true);
          player.getAbilities().flying = true;
          player.onUpdateAbilities();
        });
    JsonObject transientState = invokeSuccessfully(context, tools, "minecraft.get_player", "{}");
    JsonObject movement = transientState.getAsJsonObject("movement");
    JsonObject conditions = transientState.getAsJsonObject("conditions");
    assertTrue(movement.get("sprinting").getAsBoolean(), "sprinting state");
    assertTrue(movement.get("flying").getAsBoolean(), "flying state");
    assertTrue(!conditions.get("sleeping").getAsBoolean(), "awake state");
    assertTrue(!conditions.get("onFire").getAsBoolean(), "normal fire state");
    assertTrue(!conditions.get("fullyFrozen").getAsBoolean(), "normal frozen state");

    runOnServerAndWait(
        context,
        singleplayer,
        server -> {
          var player = server.getPlayerList().getPlayers().getFirst();
          player.setSprinting(false);
          player.getAbilities().flying = false;
          player.onUpdateAbilities();
        });
    singleplayer
        .getServer()
        .runCommand("ride @a[limit=1] mount @e[type=minecraft:minecart,limit=1,sort=nearest]");
    context.waitFor(client -> client.player != null && client.player.getVehicle() != null);
    JsonObject riding = invokeSuccessfully(context, tools, "minecraft.get_player", "{}");
    JsonObject vehicle = riding.getAsJsonObject("vehicle");
    assertEquals("minecraft:minecart", vehicle.get("entityType").getAsString(), "vehicle type");
    assertEquals("Thread Ride", vehicle.get("customName").getAsString(), "vehicle custom name");
    singleplayer.getServer().runCommand("ride @a[limit=1] dismount");
    context.waitFor(client -> client.player != null && client.player.getVehicle() == null);
    singleplayer.getServer().runCommand("tp @a[limit=1] 0.5 100 0.5 0 0");
    context.waitFor(FabricProviderClientGameTest::targetsKnownFurnace);
  }

  private static void runOnServerAndWait(
      ClientGameTestContext context,
      TestSingleplayerContext singleplayer,
      Consumer<MinecraftServer> action) {
    CompletableFuture<Void> completed = new CompletableFuture<>();
    singleplayer
        .getServer()
        .runOnServer(
            server -> {
              try {
                action.accept(server);
                completed.complete(null);
              } catch (RuntimeException exception) {
                completed.completeExceptionally(exception);
              }
            });
    context.waitFor(client -> completed.isDone());
    completed.join();
  }

  private static void verifyTargetEntityInspection(
      ClientGameTestContext context,
      TestSingleplayerContext singleplayer,
      ToolRegistry tools,
      McpHttpServer mcp) {
    singleplayer.getServer().runCommand("tp @a 0.5 100 0.5 -90 20");
    context.waitFor(client -> targetsEntity(client, "minecraft:zombie"));

    JsonObject zombie =
        mcpTool(context, mcp.endpoint(), 52, "minecraft.get_target_entity", new JsonObject());
    assertEquals("minecraft:zombie", zombie.get("entityType").getAsString(), "target entity ID");
    assertEquals("Zombie", zombie.get("displayName").getAsString(), "target display name");
    assertEquals("Thread Target", zombie.get("customName").getAsString(), "target custom name");
    assertTrue(zombie.get("living").getAsBoolean(), "target living marker");
    assertTrue(zombie.get("health").getAsDouble() > 0, "target health");
    assertTrue(
        zombie.get("maxHealth").getAsDouble() >= zombie.get("health").getAsDouble(),
        "target max health");
    assertEquals("HOSTILE", zombie.get("classification").getAsString(), "target classification");
    assertEquals("BABY", zombie.get("age").getAsString(), "target baby state");
    assertEquals(
        "minecraft:iron_sword",
        entityEquipmentItem(zombie, "MAIN_HAND").get("itemId").getAsString(),
        "target main-hand equipment");
    assertEquals(
        "minecraft:iron_helmet",
        entityEquipmentItem(zombie, "HEAD").get("itemId").getAsString(),
        "target armor equipment");
    JsonObject speed = statusEffect(zombie, "minecraft:speed");
    assertEquals(1, speed.get("amplifier").getAsInt(), "target effect amplifier");
    assertTrue(speed.get("durationTicks").getAsInt() > 0, "target effect duration");
    assertTrue(!zombie.get("activeEffectsTruncated").getAsBoolean(), "target effect completeness");
    assertTrue(zombie.get("distance").getAsDouble() <= 6, "target distance bound");

    singleplayer.getServer().runCommand("kill @e[type=minecraft:zombie]");
    singleplayer.getServer().runCommand("tp @a 0.5 100 0.5 90 25");
    context.waitFor(client -> targetsEntity(client, "minecraft:minecart"));
    JsonObject minecart = invokeSuccessfully(context, tools, "minecraft.get_target_entity", "{}");
    assertTrue(!minecart.get("living").getAsBoolean(), "non-living target marker");
    assertTrue(minecart.get("health").isJsonNull(), "non-living target health absence");
    assertTrue(minecart.getAsJsonArray("equipment").isEmpty(), "non-living target equipment");
    assertTrue(minecart.getAsJsonArray("activeEffects").isEmpty(), "non-living target effects");
    assertTrue(minecart.get("age").isJsonNull(), "non-living target age absence");

    singleplayer.getServer().runCommand("kill @e[type=minecraft:minecart]");
    singleplayer
        .getServer()
        .runCommand("summon minecraft:wolf -2 100 0 " + "{NoAI:1b,Silent:1b,Invulnerable:1b}");
    singleplayer
        .getServer()
        .runCommand(
            "data modify entity @e[type=minecraft:wolf,limit=1,sort=nearest] "
                + "Owner set from entity @a[limit=1] UUID");
    context.waitFor(client -> targetsEntity(client, "minecraft:wolf"));
    JsonObject wolf = invokeSuccessfully(context, tools, "minecraft.get_target_entity", "{}");
    assertEquals("NEUTRAL", wolf.get("classification").getAsString(), "tame target classification");
    assertEquals("ADULT", wolf.get("age").getAsString(), "tame target adult state");
    assertTrue(wolf.get("tamed").getAsBoolean(), "tame target state");
    assertTrue(!wolf.get("ownerName").getAsString().isBlank(), "tame target owner name");

    singleplayer.getServer().runCommand("kill @e[type=minecraft:wolf]");
    singleplayer
        .getServer()
        .runCommand(
            "summon minecraft:villager -2 100 0 "
                + "{NoAI:1b,Silent:1b,Invulnerable:1b,VillagerData:"
                + "{type:\"minecraft:plains\",profession:\"minecraft:librarian\",level:3}}");
    context.waitFor(client -> targetsEntity(client, "minecraft:villager"));
    JsonObject villager = invokeSuccessfully(context, tools, "minecraft.get_target_entity", "{}");
    assertEquals(
        "PASSIVE", villager.get("classification").getAsString(), "villager classification");
    assertEquals(
        "minecraft:librarian",
        villager.get("villagerProfession").getAsString(),
        "villager profession");
    assertEquals(3, villager.get("villagerLevel").getAsInt(), "villager level");

    singleplayer.getServer().runCommand("kill @e[type=minecraft:villager]");
    context.waitFor(client -> !(client.hitResult instanceof EntityHitResult));
    ToolResult<JsonElement> noTarget = invoke(context, tools, "minecraft.get_target_entity", "{}");
    assertTrue(!noTarget.successful(), "no-target entity error");
    assertEquals("NOT_FOUND", noTarget.error().code().name(), "no-target entity code");
    assertEquals(
        "The player is not currently targeting a valid entity.",
        noTarget.error().message(),
        "no-target entity message");
  }

  private static void verifyWorldEnvironmentTransitions(
      ClientGameTestContext context, TestSingleplayerContext singleplayer, ToolRegistry tools) {
    JsonObject day = invokeSuccessfully(context, tools, "minecraft.get_world_info", "{}");
    assertEquals("DAY", day.get("daylightState").getAsString(), "daylight state");
    assertTrue(!day.get("raining").getAsBoolean(), "clear weather state");
    assertTrue(!day.get("thundering").getAsBoolean(), "clear thunder state");
    assertTrue(!day.get("hardcore").getAsBoolean(), "non-hardcore test world");

    singleplayer.getServer().runCommand("time set night");
    JsonObject night = invokeSuccessfully(context, tools, "minecraft.get_world_info", "{}");
    assertEquals("NIGHT", night.get("daylightState").getAsString(), "night state transition");

    singleplayer.getServer().runCommand("weather rain");
    waitTicks(context, 25);
    JsonObject rain = invokeSuccessfully(context, tools, "minecraft.get_world_info", "{}");
    assertTrue(rain.get("raining").getAsBoolean(), "rain state transition");
    assertTrue(!rain.get("thundering").getAsBoolean(), "rain without thunder");

    singleplayer.getServer().runCommand("weather thunder");
    // Vanilla raises the thunder level by 0.01 per tick and only reports thunder above 0.9.
    waitTicks(context, 100);
    JsonObject thunder = invokeSuccessfully(context, tools, "minecraft.get_world_info", "{}");
    assertTrue(thunder.get("raining").getAsBoolean(), "thunder retains rain");
    assertTrue(thunder.get("thundering").getAsBoolean(), "thunder state transition");

    singleplayer.getServer().runCommand("weather clear");
    singleplayer.getServer().runCommand("time set day");
  }

  private static void waitTicks(ClientGameTestContext context, int ticks) {
    for (int tick = 0; tick < ticks; tick++) {
      context.waitTick();
    }
  }

  private static JsonObject mcpTool(
      ClientGameTestContext context, URI endpoint, long id, String name, JsonObject arguments) {
    return awaitExternal(
        context, () -> LoaderParityAssertions.mcpTool(endpoint, id, name, arguments));
  }

  private static JsonObject mcpToolResult(
      ClientGameTestContext context, URI endpoint, long id, String name, JsonObject arguments) {
    return awaitExternal(
        context, () -> LoaderParityAssertions.mcpToolResult(endpoint, id, name, arguments));
  }

  private static void assertToolError(
      JsonObject result, String code, String message, boolean retryable, String description) {
    assertTrue(result.get("isError").getAsBoolean(), description + " is an MCP tool error");
    JsonObject error = result.getAsJsonObject("structuredContent");
    assertEquals(code, error.get("code").getAsString(), description + " code");
    assertEquals(message, error.get("message").getAsString(), description + " message");
    assertEquals(retryable, error.get("retryable").getAsBoolean(), description + " retryability");
    assertTrue(error.getAsJsonObject("details").isEmpty(), description + " safe details");
  }

  private static JsonObject invokeSuccessfully(
      ClientGameTestContext context, ToolRegistry tools, String toolId, String input) {
    ToolResult<JsonElement> result = invoke(context, tools, toolId, input);
    if (!result.successful()) {
      throw new AssertionError(toolId + " failed: " + result.error());
    }
    if (!result.value().isJsonObject()) {
      throw new AssertionError(toolId + " returned a non-object result");
    }
    return result.value().getAsJsonObject();
  }

  private static ToolResult<JsonElement> invoke(
      ClientGameTestContext context, ToolRegistry tools, String toolId, String input) {
    JsonElement parsedInput = JsonParser.parseString(input);
    return awaitExternal(context, () -> tools.invoke(toolId, parsedInput));
  }

  private static <T> T awaitExternal(ClientGameTestContext context, Supplier<T> operation) {
    CompletableFuture<T> result = CompletableFuture.supplyAsync(operation);
    for (int elapsedTicks = 0; elapsedTicks < 200 && !result.isDone(); elapsedTicks++) {
      // Client game tests pause both logical game threads unless the test advances a tick. Pumping
      // ticks here preserves the production behavior under test: the operation itself starts on a
      // non-game thread and uses Thread's dispatchers to reach the owning client/server thread.
      context.waitTick();
    }
    if (!result.isDone()) {
      throw new AssertionError("external provider call did not finish within 200 game ticks");
    }
    return result.join();
  }

  private static boolean hasInventoryStack(Minecraft client, Item item, int count) {
    if (client.player == null) {
      return false;
    }
    for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
      if (client.player.getInventory().getItem(slot).is(item)
          && client.player.getInventory().getItem(slot).getCount() == count) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasInventoryItem(Minecraft client, Item item) {
    if (client.player == null) {
      return false;
    }
    for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
      if (client.player.getInventory().getItem(slot).is(item)) {
        return true;
      }
    }
    return false;
  }

  private static boolean targetsKnownFurnace(Minecraft client) {
    return client.hitResult instanceof BlockHitResult blockHit
        && blockHit.getBlockPos().equals(new BlockPos(0, 101, 3));
  }

  private static boolean targetsEntity(Minecraft client, String entityType) {
    return client.hitResult instanceof EntityHitResult entityHit
        && BuiltInRegistries.ENTITY_TYPE
            .getKey(entityHit.getEntity().getType())
            .toString()
            .equals(entityType);
  }

  private static JsonObject equipmentSlot(JsonObject equipment, String slot) {
    return equipment.getAsJsonArray("slots").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("slot").getAsString().equals(slot))
        .findFirst()
        .orElseThrow();
  }

  private static JsonObject equipmentItem(JsonObject equipment, String slot) {
    return equipmentSlot(equipment, slot).getAsJsonObject("item");
  }

  private static JsonObject entityEquipmentItem(JsonObject entity, String slot) {
    return entity.getAsJsonArray("equipment").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("slot").getAsString().equals(slot))
        .findFirst()
        .orElseThrow()
        .getAsJsonObject("item");
  }

  private static JsonObject statusEffect(JsonObject entity, String effectId) {
    return entity.getAsJsonArray("activeEffects").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("effectId").getAsString().equals(effectId))
        .findFirst()
        .orElseThrow();
  }

  private static JsonObject blockEntityItem(JsonObject blockEntity, String slot) {
    return blockEntity.getAsJsonArray("items").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("slot").getAsString().equals(slot))
        .map(candidate -> candidate.getAsJsonObject("item"))
        .findFirst()
        .orElseThrow();
  }

  private static JsonObject entity(JsonObject result, String entityType) {
    return result.getAsJsonArray("entities").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("entityType").getAsString().equals(entityType))
        .findFirst()
        .orElseThrow();
  }

  private static JsonObject container(JsonObject result, String blockId) {
    return result.getAsJsonArray("containers").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("blockId").getAsString().equals(blockId))
        .findFirst()
        .orElseThrow();
  }

  private static void assertDistanceOrdered(JsonObject result) {
    double previous = -1;
    for (JsonElement element : result.getAsJsonArray("containers")) {
      double current = element.getAsJsonObject().get("distance").getAsDouble();
      assertTrue(current >= previous, "container distance ordering");
      previous = current;
    }
  }

  private static JsonObject recipe(JsonObject result, String recipeId) {
    return result.getAsJsonArray("recipes").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .filter(candidate -> candidate.get("recipeId").getAsString().equals(recipeId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing recipe " + recipeId));
  }

  private static int itemCount(JsonObject inventory, String itemId) {
    return inventory.getAsJsonArray("slots").asList().stream()
        .map(JsonElement::getAsJsonObject)
        .map(slot -> slot.getAsJsonObject("stack"))
        .filter(stack -> stack.get("itemId").getAsString().equals(itemId))
        .mapToInt(stack -> stack.get("count").getAsInt())
        .sum();
  }

  private static void assertTrue(boolean condition, String description) {
    if (!condition) {
      throw new AssertionError(description);
    }
  }

  private static void assertEquals(Object expected, Object actual, String description) {
    if (!expected.equals(actual)) {
      throw new AssertionError(description + ": expected " + expected + " but was " + actual);
    }
  }
}
