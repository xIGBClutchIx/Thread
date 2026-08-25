package me.clutchy.thread.core.model.item;

import java.util.List;
import me.clutchy.thread.core.model.validation.ModelValidation;

/**
 * Whitelisted high-value item component data.
 *
 * <p>This is deliberately not a generic Minecraft component or NBT mirror. A {@code null} component
 * snapshot on {@link ItemStackInfo} means none of these selected values differ from their ordinary
 * defaults.
 *
 * @param rarity non-default serialized rarity, or {@code null}
 * @param unbreakable whether the unbreakable component is present
 * @param repairCost anvil repair cost
 * @param lore bounded plain-text lore lines
 * @param potionId base potion registry ID, or {@code null}
 * @param storedItemStacks number of non-empty stacks in an item-container component
 */
public record ItemComponentsInfo(
    String rarity,
    boolean unbreakable,
    int repairCost,
    List<String> lore,
    String potionId,
    int storedItemStacks) {
  /** Maximum lore lines retained in a Thread snapshot. */
  public static final int MAX_LORE_LINES = 16;

  /** Maximum plain-text length retained for each lore line. */
  public static final int MAX_LORE_LINE_LENGTH = 256;

  public ItemComponentsInfo {
    rarity = ModelValidation.optionalBoundedNonBlank(rarity, "rarity", 32);
    if (repairCost < 0) {
      throw new IllegalArgumentException("repairCost must not be negative");
    }
    lore = ModelValidation.immutableList(lore, "lore");
    if (lore.size() > MAX_LORE_LINES) {
      throw new IllegalArgumentException("lore must not exceed " + MAX_LORE_LINES + " lines");
    }
    lore =
        lore.stream()
            .map(line -> ModelValidation.boundedNonBlank(line, "lore line", MAX_LORE_LINE_LENGTH))
            .toList();
    potionId = ModelValidation.optionalRegistryId(potionId, "potionId");
    if (storedItemStacks < 0) {
      throw new IllegalArgumentException("storedItemStacks must not be negative");
    }
  }
}
