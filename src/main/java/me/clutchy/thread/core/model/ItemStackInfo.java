package me.clutchy.thread.core.model;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Snapshot of a non-empty item stack.
 *
 * @param itemId canonical item registry ID
 * @param displayName localized base item name, never the canonical key
 * @param customName player-assigned custom name, or {@code null}
 * @param count current stack count
 * @param maxCount maximum count allowed by the item
 * @param durability damage state for damageable items, or {@code null}
 * @param enchantments applied enchantments in canonical registry order
 * @param components selected useful component data, or {@code null} when all selected values are
 *     ordinary defaults
 */
public record ItemStackInfo(
    String itemId,
    String displayName,
    String customName,
    int count,
    int maxCount,
    ItemDurabilityInfo durability,
    List<ItemEnchantmentInfo> enchantments,
    ItemComponentsInfo components) {
  public ItemStackInfo {
    itemId = ModelValidation.registryId(itemId, "itemId");
    displayName = ModelValidation.boundedNonBlank(displayName, "displayName", 256);
    customName = ModelValidation.optionalBoundedNonBlank(customName, "customName", 256);
    if (count <= 0) {
      throw new IllegalArgumentException("count must be positive");
    }
    if (maxCount <= 0 || count > maxCount) {
      throw new IllegalArgumentException("maxCount must be positive and at least count");
    }
    enchantments =
        ModelValidation.immutableList(enchantments, "enchantments").stream()
            .sorted(Comparator.comparing(ItemEnchantmentInfo::enchantmentId))
            .toList();
    Set<String> enchantmentIds = new HashSet<>();
    for (ItemEnchantmentInfo enchantment : enchantments) {
      if (!enchantmentIds.add(enchantment.enchantmentId())) {
        throw new IllegalArgumentException(
            "item contains duplicate enchantment " + enchantment.enchantmentId());
      }
    }
  }
}
