package me.clutchy.thread.core.model.world;

import me.clutchy.thread.core.model.validation.ModelValidation;

/**
 * Bounded detached snapshot of one active living-entity status effect.
 *
 * @param effectId canonical effect registry ID
 * @param displayName localized effect name
 * @param amplifier zero-based effect amplifier
 * @param durationTicks remaining finite duration, or {@code null} when infinite
 * @param infinite whether the effect has no finite expiry
 * @param ambient whether Minecraft marks the effect as ambient
 * @param visible whether the effect emits particles
 * @param showIcon whether the effect is eligible for icon display
 */
public record StatusEffectInfo(
    String effectId,
    String displayName,
    int amplifier,
    Integer durationTicks,
    boolean infinite,
    boolean ambient,
    boolean visible,
    boolean showIcon) {
  /** Highest amplifier accepted by Minecraft's effect instance contract. */
  public static final int MAX_AMPLIFIER = 255;

  public StatusEffectInfo {
    effectId = ModelValidation.registryId(effectId, "effectId");
    displayName = ModelValidation.boundedNonBlank(displayName, "displayName", 256);
    if (amplifier < 0 || amplifier > MAX_AMPLIFIER) {
      throw new IllegalArgumentException("amplifier must be between 0 and " + MAX_AMPLIFIER);
    }
    if (infinite != (durationTicks == null)) {
      throw new IllegalArgumentException(
          "durationTicks must be absent exactly when the effect is infinite");
    }
    if (durationTicks != null && durationTicks < 0) {
      throw new IllegalArgumentException("durationTicks must not be negative");
    }
  }
}
