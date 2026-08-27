package me.clutchy.thread.core.provider;

import me.clutchy.thread.core.model.advancement.AdvancementInfo;
import me.clutchy.thread.core.model.advancement.AdvancementSnapshot;
import me.clutchy.thread.core.tool.ToolResult;

/** Supplies read-only vanilla advancement state known to the active local player. */
public interface AdvancementProvider {
  /** Returns a bounded detached snapshot of advancements currently known to the player. */
  ToolResult<AdvancementSnapshot> knownAdvancements();

  /** Returns detailed live progress for one known canonical advancement ID. */
  ToolResult<AdvancementInfo> advancement(String advancementId);
}
