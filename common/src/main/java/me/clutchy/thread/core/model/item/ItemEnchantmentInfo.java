package me.clutchy.thread.core.model.item;

import me.clutchy.thread.core.model.validation.ModelValidation;

/** Canonical identity and level of one enchantment applied to an item stack. */
public record ItemEnchantmentInfo(String enchantmentId, int level) {
  public ItemEnchantmentInfo {
    enchantmentId = ModelValidation.registryId(enchantmentId, "enchantmentId");
    if (level <= 0) {
      throw new IllegalArgumentException("level must be positive");
    }
  }
}
