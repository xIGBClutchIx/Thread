package me.clutchy.thread.gametest;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** World controls used only by the automated graphical proofs. */
public final class ClientTestWorld {
  private ClientTestWorld() {}

  /** Selects Minecraft's built-in flat preset to keep world generation deterministic and small. */
  public static void selectFlatPreset(CreateWorldScreen screen) {
    WorldCreationUiState state = screen.getUiState();
    WorldCreationUiState.WorldTypeEntry flatPreset =
        state.getNormalPresetList().stream()
            .filter(entry -> entry.preset().is(WorldPresets.FLAT))
            .findFirst()
            .orElseThrow(() -> new AssertionError("flat world preset was not found"));
    state.setWorldType(flatPreset);
  }
}
