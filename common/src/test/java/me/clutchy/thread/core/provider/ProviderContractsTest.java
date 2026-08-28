package me.clutchy.thread.core.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.clutchy.thread.core.model.advancement.AdvancementCriterionInfo;
import me.clutchy.thread.core.model.advancement.AdvancementDisplayType;
import me.clutchy.thread.core.model.advancement.AdvancementInfo;
import me.clutchy.thread.core.model.advancement.AdvancementSnapshot;
import me.clutchy.thread.core.model.game.GameInfo;
import me.clutchy.thread.core.model.game.SessionState;
import me.clutchy.thread.core.model.game.SessionStatus;
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
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.ContainerInspectionQuery;
import me.clutchy.thread.core.model.world.DaylightState;
import me.clutchy.thread.core.model.world.EntityClassification;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyContainerSnapshotResult;
import me.clutchy.thread.core.model.world.NearbyContainerSummary;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.model.world.Position;
import me.clutchy.thread.core.model.world.WorldInfo;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class ProviderContractsTest {
  @Test
  void plainJavaFakesCanDriveEveryProviderContract() {
    ItemStackInfo pickaxe = item("minecraft:iron_pickaxe", "Iron Pickaxe", 1, 1);
    GameProvider game = new FakeGameProvider();
    AdvancementProvider advancements = new FakeAdvancementProvider();
    PlayerProvider player = new FakePlayerProvider(pickaxe);
    WorldProvider world = new FakeWorldProvider();
    RecipeProvider recipes = new FakeRecipeProvider();

    assertTrue(game.sessionStatus().supported());
    assertEquals("26.2", game.gameInfo().minecraftVersion());
    assertEquals(
        "minecraft:story/root",
        advancements.knownAdvancements().value().advancements().getFirst().advancementId());
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
    assertEquals("minecraft:plains", world.worldInfo().value().biomeId());
    assertEquals("minecraft:zombie", world.targetEntity().value().orElseThrow().entityType());
    assertEquals(
        "minecraft:zombie",
        world
            .nearbyEntities(new NearbyEntityQuery(16, 10))
            .value()
            .entities()
            .getFirst()
            .entityType());
    assertEquals(
        "minecraft:barrel",
        world
            .nearbyContainers(new NearbyContainerQuery(12, 10))
            .value()
            .containers()
            .getFirst()
            .blockId());
    assertEquals(
        "minecraft:barrel",
        world
            .nearbyContainerSnapshots(new NearbyContainerQuery(12, 10))
            .value()
            .containers()
            .getFirst()
            .blockId());
    assertEquals(
        27,
        world
            .inspectContainer(new ContainerInspectionQuery(new BlockPosition(2, 64, 0)))
            .value()
            .blockEntity()
            .inventorySize());
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
      return new GameInfo("26.2", "fabric", "0.19.3", "1.0.0");
    }
  }

  private static final class FakeAdvancementProvider implements AdvancementProvider {
    private static final AdvancementInfo ROOT =
        new AdvancementInfo(
            "minecraft:story/root",
            "Minecraft",
            "The heart and story of the game",
            false,
            0,
            0,
            1,
            0,
            1,
            false,
            null,
            "minecraft:story/root",
            "Minecraft",
            AdvancementDisplayType.TASK,
            false,
            null,
            null,
            List.of(new AdvancementCriterionInfo("crafting_table", false, null)));

    @Override
    public ToolResult<AdvancementSnapshot> knownAdvancements() {
      return ToolResult.success(new AdvancementSnapshot(1, 64, false, List.of(ROOT)));
    }

    @Override
    public ToolResult<AdvancementInfo> advancement(String advancementId) {
      return ToolResult.success(ROOT);
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
              "survival",
              false,
              new PlayerStatus.Armor(0, 0),
              new PlayerStatus.Air(300, 300),
              List.of(),
              false,
              new PlayerStatus.Movement(false, false, false, false, true, 0),
              new PlayerStatus.Conditions(false, false, false, false),
              0,
              1,
              null,
              null));
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
    public ToolResult<WorldInfo> worldInfo() {
      return ToolResult.success(
          new WorldInfo(
              "minecraft:overworld",
              "minecraft:plains",
              "Plains",
              new Position(0.5, 64.5, 0.5),
              "minecraft:overworld",
              new BlockPosition(0, 64, 0),
              0.0,
              "normal",
              false,
              1_234,
              6_000,
              0,
              6_000,
              DaylightState.DAY,
              false,
              false,
              15,
              "full_moon",
              0.8,
              true));
    }

    @Override
    public ToolResult<Optional<EntityInfo>> targetEntity() {
      return ToolResult.success(Optional.of(zombie()));
    }

    @Override
    public ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query) {
      return ToolResult.success(
          new NearbyEntityResult(query.radius(), query.limit(), false, List.of(zombie())));
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
                      "minecraft:barrel",
                      "minecraft:barrel",
                      "Barrel",
                      new BlockPosition(2, 64, 0),
                      1,
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
                      "minecraft:barrel",
                      "Barrel",
                      new BlockPosition(2, 64, 0),
                      Map.of(),
                      1,
                      true,
                      new BlockEntityInfo("minecraft:barrel", 27, List.of(), Map.of())))));
    }

    @Override
    public ToolResult<BlockInfo> inspectContainer(ContainerInspectionQuery query) {
      return ToolResult.success(
          new BlockInfo(
              "minecraft:barrel",
              "Barrel",
              query.position(),
              Map.of(),
              1,
              true,
              new BlockEntityInfo("minecraft:barrel", 27, List.of(), Map.of())));
    }
  }

  private static EntityInfo zombie() {
    return new EntityInfo(
        "minecraft:zombie",
        "Zombie",
        null,
        4,
        new Position(4, 64, 0),
        true,
        20.0,
        20.0,
        EntityClassification.HOSTILE,
        List.of(),
        List.of(),
        false,
        null,
        null,
        null,
        null,
        null);
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
