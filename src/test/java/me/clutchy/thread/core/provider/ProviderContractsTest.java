package me.clutchy.thread.core.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.clutchy.thread.core.model.BlockInfo;
import me.clutchy.thread.core.model.BlockPosition;
import me.clutchy.thread.core.model.EntityClassification;
import me.clutchy.thread.core.model.EntityInfo;
import me.clutchy.thread.core.model.EquipmentPosition;
import me.clutchy.thread.core.model.EquipmentSlotInfo;
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
    ItemStackInfo pickaxe = item("minecraft:iron_pickaxe", "Iron Pickaxe", 1, 1);
    GameProvider game = new FakeGameProvider();
    PlayerProvider player = new FakePlayerProvider(pickaxe);
    WorldProvider world = new FakeWorldProvider();
    RecipeProvider recipes = new FakeRecipeProvider();

    assertTrue(game.sessionStatus().supported());
    assertEquals("26.2", game.gameInfo().minecraftVersion());
    assertEquals("minecraft:overworld", player.status().value().dimension());
    assertEquals(pickaxe, player.inventory().value().slots().getFirst().stack());
    assertEquals(
        pickaxe,
        player.equipment().value().slots().stream()
            .filter(slot -> slot.slot() == EquipmentPosition.MAIN_HAND)
            .findFirst()
            .orElseThrow()
            .item());
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
      return ToolResult.success(equipmentWithMainHand(pickaxe));
    }

    @Override
    public ToolResult<Optional<BlockInfo>> targetBlock() {
      return ToolResult.success(
          Optional.of(
              new BlockInfo(
                  "minecraft:stone",
                  "Stone",
                  new BlockPosition(1, 63, -3),
                  Map.of(),
                  2.25,
                  false,
                  null)));
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
                      4,
                      new Position(4, 64, 0),
                      true,
                      20.0,
                      20.0,
                      EntityClassification.HOSTILE))));
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
                  item("minecraft:iron_pickaxe", "Iron Pickaxe", 1, 1),
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
