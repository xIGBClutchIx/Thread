package dev.xigbclutch.thread.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.xigbclutch.thread.core.error.ToolError;
import dev.xigbclutch.thread.core.error.ToolErrorCode;
import dev.xigbclutch.thread.core.tool.ToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreModelTest {
  @Test
  void inventorySnapshotsAreDetachedSortedAndRejectDuplicateSlots() {
    ItemStackInfo stone = new ItemStackInfo("minecraft:stone", 32, 64, "Stone");
    List<InventorySlotInfo> source = new ArrayList<>();
    source.add(new InventorySlotInfo(8, stone));
    source.add(new InventorySlotInfo(1, stone));

    InventorySnapshot snapshot = new InventorySnapshot(1, source);
    source.clear();

    assertEquals(List.of(1, 8), snapshot.slots().stream().map(InventorySlotInfo::slot).toList());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.slots().clear());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new InventorySnapshot(
                0, List.of(new InventorySlotInfo(1, stone), new InventorySlotInfo(1, stone))));
  }

  @Test
  void recipeIngredientAlternativesRemainCompleteAndDeterministic() {
    RecipeIngredientInfo ingredient =
        new RecipeIngredientInfo(
            List.of("minecraft:oak_planks", "minecraft:birch_planks", "minecraft:oak_planks"),
            List.of("minecraft:planks"),
            2);

    assertEquals(List.of("minecraft:birch_planks", "minecraft:oak_planks"), ingredient.itemIds());
    assertEquals(List.of("minecraft:planks"), ingredient.tagIds());
    assertEquals(2, ingredient.count());
  }

  @Test
  void errorsDetachAndSortDiagnosticDetails() {
    Map<String, String> source = new LinkedHashMap<>();
    source.put("zulu", "last");
    source.put("alpha", "first");

    ToolError error = new ToolError(ToolErrorCode.INVALID_INPUT, "Invalid.", false, source);
    source.clear();

    assertEquals(List.of("alpha", "zulu"), error.details().keySet().stream().toList());
    assertThrows(UnsupportedOperationException.class, () -> error.details().clear());
  }

  @Test
  void toolResultsRequireExactlyOneOutcome() {
    ToolError error = ToolError.of(ToolErrorCode.NOT_AVAILABLE, "Unavailable.", true);

    assertEquals("ok", ToolResult.success("ok").value());
    assertEquals(error, ToolResult.failure(error).error());
    assertThrows(IllegalArgumentException.class, () -> new ToolResult<>(null, null));
    assertThrows(IllegalArgumentException.class, () -> new ToolResult<>("value", error));
  }

  @Test
  void onlyLoadedSingleplayerCanBeReportedAsSupported() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SessionStatus(SessionState.MULTIPLAYER, true, true, true, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SessionStatus(SessionState.MAIN_MENU, false, false, false, null));
  }
}
