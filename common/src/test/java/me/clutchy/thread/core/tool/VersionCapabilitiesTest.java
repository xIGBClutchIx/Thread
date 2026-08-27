package me.clutchy.thread.core.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VersionCapabilitiesTest {
  @Test
  void absentToolsDefaultToUnsupportedAndAreNotAdvertised() {
    VersionCapabilities capabilities =
        VersionCapabilities.of(
            Map.of(ToolId.of("minecraft.get_status"), ToolSupport.fullySupported()));

    assertTrue(capabilities.advertises(ToolId.of("minecraft.get_status")));
    assertFalse(capabilities.advertises(ToolId.of("minecraft.get_player")));
    assertEquals(
        ToolSupport.Level.UNSUPPORTED,
        capabilities.support(ToolId.of("minecraft.get_player")).level());
  }

  @Test
  void missingOptionalFieldsRemainSupportedAndAreNormalized() {
    ToolSupport support =
        ToolSupport.missingOptionalFields(
            List.of("world.biomeName", "player.respawn", "world.biomeName"));

    assertTrue(support.advertised());
    assertEquals(ToolSupport.Level.SUPPORTED_WITH_MISSING_OPTIONAL_FIELDS, support.level());
    assertEquals(List.of("player.respawn", "world.biomeName"), support.missingOptionalFields());
  }

  @Test
  void supportStatesRejectContradictoryOptionalFieldDeclarations() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ToolSupport(ToolSupport.Level.FULLY_SUPPORTED, List.of("world.biomeName")));
    assertThrows(
        IllegalArgumentException.class, () -> ToolSupport.missingOptionalFields(List.of()));
  }
}
