package me.clutchy.thread.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.advancement.AdvancementCriterionInfo;
import me.clutchy.thread.core.model.advancement.AdvancementDisplayType;
import me.clutchy.thread.core.model.advancement.AdvancementFilter;
import me.clutchy.thread.core.model.advancement.AdvancementInfo;
import me.clutchy.thread.core.model.advancement.AdvancementListQuery;
import me.clutchy.thread.core.model.advancement.AdvancementListResult;
import me.clutchy.thread.core.model.advancement.AdvancementLookupQuery;
import me.clutchy.thread.core.model.advancement.AdvancementSnapshot;
import me.clutchy.thread.core.provider.AdvancementProvider;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class AdvancementServiceTest {
  private static final String ROOT = "minecraft:story/root";
  private static final String PARTIAL = "minecraft:story/mine_stone";
  private static final String COMPLETE = "minecraft:story/smelt_iron";

  @Test
  void defaultsToAllAndReturnsDeterministicKnownAdvancements() {
    AdvancementService service = service(List.of(partial(), root(), completed()));

    AdvancementListResult result = service.list(new AdvancementListQuery(null, null, null)).value();

    assertEquals(AdvancementFilter.ALL, result.filter());
    assertEquals(AdvancementListQuery.DEFAULT_LIMIT, result.limit());
    assertEquals(3, result.knownCount());
    assertEquals(3, result.scannedCount());
    assertEquals(3, result.matchedCount());
    assertFalse(result.sourceTruncated());
    assertFalse(result.truncated());
    assertEquals(
        List.of(PARTIAL, ROOT, COMPLETE),
        result.advancements().stream().map(value -> value.advancementId()).toList());
  }

  @Test
  void filtersCompletedAndIncompleteProgressWithoutLosingPartialCriteria() {
    AdvancementService service = service(List.of(root(), partial(), completed()));

    AdvancementListResult completed =
        service.list(new AdvancementListQuery(AdvancementFilter.COMPLETED, null, 10)).value();
    AdvancementListResult incomplete =
        service.list(new AdvancementListQuery(AdvancementFilter.INCOMPLETE, null, 10)).value();
    AdvancementInfo partial = service.get(new AdvancementLookupQuery(PARTIAL)).value();

    assertEquals(List.of(COMPLETE), ids(completed));
    assertEquals(List.of(PARTIAL, ROOT), ids(incomplete));
    assertEquals(1, partial.completedCriteria());
    assertEquals(2, partial.totalCriteria());
    assertEquals(50.0, partial.completionPercentage());
    assertEquals(
        List.of(false, true),
        partial.criteria().stream().map(value -> value.completed()).sorted().toList());
    assertEquals(ROOT, partial.parentAdvancementId());
    assertEquals(ROOT, partial.tabAdvancementId());
  }

  @Test
  void searchesExactIdsAndFriendlyDisplayText() {
    AdvancementService service = service(List.of(root(), partial(), completed()));

    AdvancementListResult exact =
        service.list(new AdvancementListQuery(null, COMPLETE, 10)).value();
    AdvancementListResult friendly =
        service.list(new AdvancementListQuery(null, "mine stone", 10)).value();

    assertEquals(List.of(COMPLETE), ids(exact));
    assertEquals(List.of(PARTIAL), ids(friendly));
  }

  @Test
  void reportsQueryAndProviderTruncationSeparately() {
    AdvancementProvider provider =
        new FakeProvider(
            new AdvancementSnapshot(5, 4, true, List.of(root(), partial(), completed(), other())),
            List.of(root(), partial(), completed(), other()));
    AdvancementListResult result =
        new AdvancementService(provider)
            .list(new AdvancementListQuery(AdvancementFilter.ALL, null, 2))
            .value();

    assertEquals(5, result.knownCount());
    assertEquals(4, result.scannedCount());
    assertEquals(4, result.matchedCount());
    assertEquals(2, result.advancements().size());
    assertTrue(result.sourceTruncated());
    assertTrue(result.truncated());
  }

  @Test
  void exposesHiddenAndCompletionTimestampDetails() {
    AdvancementInfo info =
        service(List.of(completed())).get(new AdvancementLookupQuery(COMPLETE)).value();

    assertTrue(info.completed());
    assertEquals(Boolean.TRUE, info.hidden());
    assertEquals(AdvancementDisplayType.CHALLENGE, info.displayType());
    assertEquals("2026-08-26T12:02:00Z", info.completedAt());
    assertFalse(info.criteriaTruncated());
  }

  @Test
  void rejectsUnknownIdsAndPropagatesMenuAndMultiplayerFailures() {
    AdvancementService service = service(List.of(root()));
    ToolResult<AdvancementInfo> unknown =
        service.get(new AdvancementLookupQuery("minecraft:story/unknown"));
    AdvancementService menu = failing(ToolErrorCode.WORLD_NOT_AVAILABLE);
    AdvancementService multiplayer = failing(ToolErrorCode.UNSUPPORTED);

    assertEquals(ToolErrorCode.NOT_FOUND, unknown.error().code());
    assertEquals(
        ToolErrorCode.WORLD_NOT_AVAILABLE,
        menu.list(new AdvancementListQuery(null, null, null)).error().code());
    assertEquals(
        ToolErrorCode.UNSUPPORTED,
        multiplayer.get(new AdvancementLookupQuery(ROOT)).error().code());
  }

  private static AdvancementService service(List<AdvancementInfo> values) {
    return new AdvancementService(
        new FakeProvider(new AdvancementSnapshot(values.size(), 100, false, values), values));
  }

  private static AdvancementService failing(ToolErrorCode code) {
    ToolError error = ToolError.of(code, "Unavailable for test.", true);
    return new AdvancementService(
        new AdvancementProvider() {
          @Override
          public ToolResult<AdvancementSnapshot> knownAdvancements() {
            return ToolResult.failure(error);
          }

          @Override
          public ToolResult<AdvancementInfo> advancement(String advancementId) {
            return ToolResult.failure(error);
          }
        });
  }

  private static List<String> ids(AdvancementListResult result) {
    return result.advancements().stream().map(value -> value.advancementId()).toList();
  }

  private static AdvancementInfo root() {
    return info(
        ROOT,
        "Minecraft",
        "The heart and story of the game",
        false,
        0,
        0,
        1,
        0,
        1,
        null,
        false,
        AdvancementDisplayType.TASK,
        null,
        null,
        List.of(new AdvancementCriterionInfo("crafting_table", false, null)));
  }

  private static AdvancementInfo partial() {
    return info(
        PARTIAL,
        "Stone Age",
        "Mine stone with your new pickaxe",
        false,
        50,
        1,
        2,
        1,
        2,
        ROOT,
        false,
        AdvancementDisplayType.TASK,
        "2026-08-26T12:00:00Z",
        null,
        List.of(
            new AdvancementCriterionInfo("mine_stone", true, "2026-08-26T12:00:00Z"),
            new AdvancementCriterionInfo("obtain_cobblestone", false, null)));
  }

  private static AdvancementInfo completed() {
    return info(
        COMPLETE,
        "Acquire Hardware",
        "Smelt an iron ingot",
        true,
        100,
        2,
        2,
        2,
        2,
        ROOT,
        true,
        AdvancementDisplayType.CHALLENGE,
        "2026-08-26T12:01:00Z",
        "2026-08-26T12:02:00Z",
        List.of(
            new AdvancementCriterionInfo("iron", true, "2026-08-26T12:02:00Z"),
            new AdvancementCriterionInfo("furnace", true, "2026-08-26T12:01:00Z")));
  }

  private static AdvancementInfo other() {
    return info(
        "minecraft:adventure/root",
        "Adventure",
        "Adventure, exploration and combat",
        false,
        0,
        0,
        1,
        0,
        1,
        null,
        false,
        AdvancementDisplayType.TASK,
        null,
        null,
        List.of(new AdvancementCriterionInfo("killed_something", false, null)));
  }

  private static AdvancementInfo info(
      String id,
      String title,
      String description,
      boolean completed,
      double percentage,
      int completedCriteria,
      int totalCriteria,
      int completedRequirements,
      int totalRequirements,
      String parent,
      boolean hidden,
      AdvancementDisplayType displayType,
      String firstProgressAt,
      String completedAt,
      List<AdvancementCriterionInfo> criteria) {
    return new AdvancementInfo(
        id,
        title,
        description,
        completed,
        percentage,
        completedCriteria,
        totalCriteria,
        completedRequirements,
        totalRequirements,
        false,
        parent,
        ROOT,
        "Minecraft",
        displayType,
        hidden,
        firstProgressAt,
        completedAt,
        criteria);
  }

  private record FakeProvider(AdvancementSnapshot snapshot, List<AdvancementInfo> advancements)
      implements AdvancementProvider {
    @Override
    public ToolResult<AdvancementSnapshot> knownAdvancements() {
      return ToolResult.success(snapshot);
    }

    @Override
    public ToolResult<AdvancementInfo> advancement(String advancementId) {
      return advancements.stream()
          .filter(value -> value.advancementId().equals(advancementId))
          .findFirst()
          .map(ToolResult::success)
          .orElseGet(
              () ->
                  ToolResult.failure(
                      ToolError.of(
                          ToolErrorCode.NOT_FOUND,
                          "The requested advancement is not known to the player.",
                          false)));
    }
  }
}
