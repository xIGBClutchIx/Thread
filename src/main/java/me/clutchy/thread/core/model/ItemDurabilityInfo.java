package me.clutchy.thread.core.model;

/**
 * Remaining and consumed durability for a damageable item stack.
 *
 * @param remaining usable durability before the item breaks
 * @param maximum maximum durability of a new item
 * @param damage durability already consumed
 */
public record ItemDurabilityInfo(int remaining, int maximum, int damage) {
  public ItemDurabilityInfo {
    if (maximum <= 0) {
      throw new IllegalArgumentException("maximum must be positive");
    }
    if (damage < 0 || damage > maximum) {
      throw new IllegalArgumentException("damage must be between zero and maximum");
    }
    if (remaining != maximum - damage) {
      throw new IllegalArgumentException("remaining must equal maximum minus damage");
    }
  }
}
