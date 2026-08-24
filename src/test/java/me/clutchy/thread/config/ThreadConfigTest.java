package me.clutchy.thread.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.clutchy.thread.core.tool.ToolId;
import org.junit.jupiter.api.Test;

class ThreadConfigTest {
  @Test
  void defaultsAreLoopbackOnlyAndBounded() {
    ThreadConfig defaults = ThreadConfig.defaults();

    assertTrue(defaults.mcpEnabled());
    assertEquals("127.0.0.1", defaults.mcpBindHost());
    assertEquals(List.of("minecraft.*"), defaults.enabledTools());
    assertTrue(defaults.toolEnabled(ToolId.of("minecraft.get_status")));
  }

  @Test
  void selectorsAreNormalizedAndCanDisableIndividualTools() {
    ThreadConfig config = configured(List.of("minecraft.get_status", "MINECRAFT.GET_STATUS"));

    assertEquals(List.of("minecraft.get_status"), config.enabledTools());
    assertTrue(config.toolEnabled(ToolId.of("minecraft.get_status")));
    assertFalse(config.toolEnabled(ToolId.of("minecraft.get_player")));
  }

  @Test
  void unsafeHostsAndLimitsAboveHardCeilingsAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ThreadConfig(
                true, "0.0.0.0", 25_580, List.of("minecraft.*"), 64, 128, 64, 1024, 1000, 8));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ThreadConfig(
                true,
                "127.0.0.1",
                25_580,
                List.of("minecraft.*"),
                ThreadConfig.HARD_MAX_ENTITY_RADIUS + 1,
                128,
                64,
                1024,
                1000,
                8));
  }

  private static ThreadConfig configured(List<String> selectors) {
    return new ThreadConfig(true, "localhost", 25_580, selectors, 64, 128, 64, 1024, 1000, 8);
  }
}
