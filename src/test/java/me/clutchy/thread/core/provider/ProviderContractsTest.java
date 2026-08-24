package me.clutchy.thread.core.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.clutchy.thread.core.model.BlockInfo;
import me.clutchy.thread.core.model.BlockPosition;
import me.clutchy.thread.core.model.EntityInfo;
import me.clutchy.thread.core.model.EquipmentSnapshot;
import me.clutchy.thread.core.model.GameInfo;
import me.clutchy.thread.core.model.InventorySlotInfo;
import me.clutchy.thread.core.model.InventorySnapshot;
import me.clutchy.thread.core.model.ItemInfo;
import me.clutchy.thread.core.model.ItemSearchResult;
import me.clutchy.thread.core.model.ItemStackInfo;
import me.clutchy.thread.core.model.NearbyEntityQuery;
import me.clutchy.thread.core.model.NearbyEntityResult;
import me.clutchy.thread.core.model.PlayerStatus;
import me.clutchy.thread.core.model.Position;
import me.clutchy.thread.core.model.RecipeInfo;
import me.clutchy.thread.core.model.RecipeIngredientInfo;
import me.clutchy.thread.core.model.SessionState;
import me.clutchy.thread.core.model.SessionStatus;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class ProviderContractsTest {
  @Test
  void plainJavaFakesCanDriveEveryProviderContract() {
    ItemStackInfo pickaxe = new ItemStackInfo("minecraft:iron_pickaxe", 1, 1, "Iron Pickaxe");
    GameProvider game = new FakeGameProvider();
    PlayerProvider player = new FakePlayerProvider(pickaxe);
    WorldProvider world = new FakeWorldProvider();
    RecipeProvider recipes = new FakeRecipeProvider();

    assertTrue(game.sessionStatus().supported());
    assertEquals("26.2", game.gameInfo().minecraftVersion());
    assertEquals("minecraft:overworld", player.status().value().dimension());
    assertEquals(pickaxe, player.inventory().value().slots().getFirst().stack());
    assertEquals(pickaxe, player.equipment().value().mainHand());
    assertEquals("minecraft:stone", player.targetBlock().value().orElseThrow().blockId());
    assertEquals(
        "minecraft:zombie",
        world
            .nearbyEntities(new NearbyEntityQuery(16, 10))
            .value()
            .entities()
            .getFirst()
            .entityType());
    assertEquals(
        "minecraft:stick", recipes.searchItems("stick", 5).value().items().getFirst().itemId());
    assertEquals(
        "minecraft:iron_pickaxe",
        recipes.recipesFor("minecraft:iron_pickaxe").value().getFirst().result().itemId());
  }

  private static final class FakeGameProvider implements GameProvider {
    @Override
    public SessionStatus sessionStatus() {
      return new SessionStatus(SessionState.SINGLEPLAYER, true, true, true, null);
    }

    @Override
    public GameInfo gameInfo() {
      return new GameInfo("26.2", "fabric", "0.19.3", "0.1.0");
    }
  }

  private record FakePlayerProvider(ItemStackInfo pickaxe) implements PlayerProvider {
    @Override
    public ToolResult<PlayerStatus> status() {
      return ToolResult.success(
          new PlayerStatus(
              20,
              20,
              20,
              5,
              3,
              0.5,
              new Position(1.5, 64, -2.5),
              "minecraft:overworld",
              "survival"));
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
      return ToolResult.success(
          new InventorySnapshot(0, List.of(new InventorySlotInfo(0, pickaxe))));
    }

    @Override
    public ToolResult<EquipmentSnapshot> equipment() {
      return ToolResult.success(new EquipmentSnapshot(pickaxe, null, null, null, null, null));
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      return ToolResult.success(
          Optional.of(
              new BlockInfo("minecraft:stone", new BlockPosition(1, 63, -3), Map.of(), 2.25)));
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
              List.of(new EntityInfo("minecraft:zombie", 4, new Position(4, 64, 0)))));
    }
  }

  private static final class FakeRecipeProvider implements RecipeProvider {
    @Override
    public ToolResult<List<RecipeInfo>> recipesFor(String itemId) {
      return ToolResult.success(
          List.of(
              new RecipeInfo(
                  "minecraft:iron_pickaxe",
                  "minecraft:crafting_shaped",
                  new ItemStackInfo("minecraft:iron_pickaxe", 1, 1, "Iron Pickaxe"),
                  List.of(
                      new RecipeIngredientInfo(List.of("minecraft:iron_ingot"), List.of(), 3),
                      new RecipeIngredientInfo(List.of("minecraft:stick"), List.of(), 2)))));
    }

    @Override
    public ToolResult<ItemSearchResult> searchItems(String query, int limit) {
      return ToolResult.success(
          new ItemSearchResult(
              query, limit, false, List.of(new ItemInfo("minecraft:stick", "Stick"))));
    }
  }
}
