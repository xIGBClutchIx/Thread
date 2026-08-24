package dev.xigbclutch.thread.core.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.xigbclutch.thread.core.model.BlockInfo;
import dev.xigbclutch.thread.core.model.BlockPosition;
import dev.xigbclutch.thread.core.model.EntityInfo;
import dev.xigbclutch.thread.core.model.EquipmentSnapshot;
import dev.xigbclutch.thread.core.model.GameInfo;
import dev.xigbclutch.thread.core.model.InventorySlotInfo;
import dev.xigbclutch.thread.core.model.InventorySnapshot;
import dev.xigbclutch.thread.core.model.ItemInfo;
import dev.xigbclutch.thread.core.model.ItemStackInfo;
import dev.xigbclutch.thread.core.model.NearbyEntityQuery;
import dev.xigbclutch.thread.core.model.NearbyEntityResult;
import dev.xigbclutch.thread.core.model.PlayerStatus;
import dev.xigbclutch.thread.core.model.Position;
import dev.xigbclutch.thread.core.model.RecipeInfo;
import dev.xigbclutch.thread.core.model.RecipeIngredientInfo;
import dev.xigbclutch.thread.core.model.SessionState;
import dev.xigbclutch.thread.core.model.SessionStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    assertEquals("minecraft:overworld", player.status().dimension());
    assertEquals(pickaxe, player.inventory().slots().getFirst().stack());
    assertEquals(pickaxe, player.equipment().mainHand());
    assertEquals("minecraft:stone", player.targetBlock().orElseThrow().blockId());
    assertEquals(
        "minecraft:zombie",
        world.nearbyEntities(new NearbyEntityQuery(16, 10)).entities().getFirst().entityType());
    assertEquals("minecraft:stick", recipes.searchItems("stick", 5).getFirst().itemId());
    assertEquals(
        "minecraft:iron_pickaxe",
        recipes.recipesFor("minecraft:iron_pickaxe").getFirst().result().itemId());
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
    public PlayerStatus status() {
      return new PlayerStatus(
          20, 20, 20, 5, 3, 0.5, new Position(1.5, 64, -2.5), "minecraft:overworld", "survival");
    }

    @Override
    public InventorySnapshot inventory() {
      return new InventorySnapshot(0, List.of(new InventorySlotInfo(0, pickaxe)));
    }

    @Override
    public EquipmentSnapshot equipment() {
      return new EquipmentSnapshot(pickaxe, null, null, null, null, null);
    }

    @Override
    public Optional<BlockInfo> targetBlock() {
      return Optional.of(
          new BlockInfo("minecraft:stone", new BlockPosition(1, 63, -3), Map.of(), 2.25));
    }
  }

  private static final class FakeWorldProvider implements WorldProvider {
    @Override
    public NearbyEntityResult nearbyEntities(NearbyEntityQuery query) {
      return new NearbyEntityResult(
          query.radius(),
          query.limit(),
          false,
          List.of(new EntityInfo("minecraft:zombie", 4, new Position(4, 64, 0))));
    }
  }

  private static final class FakeRecipeProvider implements RecipeProvider {
    @Override
    public List<RecipeInfo> recipesFor(String itemId) {
      return List.of(
          new RecipeInfo(
              "minecraft:iron_pickaxe",
              "minecraft:crafting_shaped",
              new ItemStackInfo("minecraft:iron_pickaxe", 1, 1, "Iron Pickaxe"),
              List.of(
                  new RecipeIngredientInfo(List.of("minecraft:iron_ingot"), List.of(), 3),
                  new RecipeIngredientInfo(List.of("minecraft:stick"), List.of(), 2))));
    }

    @Override
    public List<ItemInfo> searchItems(String query, int limit) {
      return List.of(new ItemInfo("minecraft:stick", "Stick"));
    }
  }
}
