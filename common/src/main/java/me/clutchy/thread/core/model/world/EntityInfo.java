package me.clutchy.thread.core.model.world;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.player.EquipmentSlotInfo;
import me.clutchy.thread.core.model.validation.ModelValidation;

/**
 * Bounded summary of one already-loaded entity observed by the local player.
 *
 * <p>Classification is nullable because Thread only labels entity families that Minecraft exposes
 * through reliable behavior markers. Rich living metadata stays conditional and contains no raw
 * entity data or NBT.
 */
public record EntityInfo(
    String entityType,
    String displayName,
    String customName,
    double distance,
    Position position,
    boolean living,
    Double health,
    Double maxHealth,
    EntityClassification classification,
    List<EquipmentSlotInfo> equipment,
    List<StatusEffectInfo> activeEffects,
    boolean activeEffectsTruncated,
    EntityAgeState age,
    Boolean tamed,
    String ownerName,
    String villagerProfession,
    Integer villagerLevel) {
  /** Maximum number of active effects serialized for one entity. */
  public static final int MAX_ACTIVE_EFFECTS = 64;

  public EntityInfo {
    entityType = ModelValidation.registryId(entityType, "entityType");
    displayName = ModelValidation.boundedNonBlank(displayName, "displayName", 256);
    customName = ModelValidation.optionalBoundedNonBlank(customName, "customName", 256);
    ModelValidation.nonNegative(distance, "distance");
    Objects.requireNonNull(position, "position");
    equipment =
        ModelValidation.immutableList(equipment, "equipment").stream()
            .sorted(Comparator.comparing(EquipmentSlotInfo::slot))
            .toList();
    activeEffects =
        ModelValidation.immutableList(activeEffects, "activeEffects").stream()
            .sorted(Comparator.comparing(StatusEffectInfo::effectId))
            .toList();
    validateEquipment(equipment);
    validateEffects(activeEffects, activeEffectsTruncated);
    ownerName = ModelValidation.optionalBoundedNonBlank(ownerName, "ownerName", 256);
    villagerProfession =
        ModelValidation.optionalRegistryId(villagerProfession, "villagerProfession");
    if (living) {
      if (health == null || maxHealth == null) {
        throw new IllegalArgumentException("living entities must include health and maxHealth");
      }
      ModelValidation.nonNegative(health, "health");
      if (!Double.isFinite(maxHealth) || maxHealth <= 0) {
        throw new IllegalArgumentException("maxHealth must be finite and positive");
      }
    } else if (health != null
        || maxHealth != null
        || classification != null
        || !equipment.isEmpty()
        || !activeEffects.isEmpty()
        || activeEffectsTruncated
        || age != null
        || tamed != null
        || ownerName != null
        || villagerProfession != null
        || villagerLevel != null) {
      throw new IllegalArgumentException(
          "non-living entities cannot include living-specific metadata");
    }
    if (ownerName != null && !Boolean.TRUE.equals(tamed)) {
      throw new IllegalArgumentException("ownerName requires a tamed entity");
    }
    if ((villagerProfession == null) != (villagerLevel == null)) {
      throw new IllegalArgumentException(
          "villagerProfession and villagerLevel must be present together");
    }
    if (villagerLevel != null && (villagerLevel < 1 || villagerLevel > 5)) {
      throw new IllegalArgumentException("villagerLevel must be between 1 and 5");
    }
  }

  private static void validateEquipment(List<EquipmentSlotInfo> equipment) {
    if (equipment.size() > EquipmentPosition.values().length) {
      throw new IllegalArgumentException("equipment exceeds the supported slot count");
    }
    Set<EquipmentPosition> positions = new HashSet<>();
    for (EquipmentSlotInfo slot : equipment) {
      if (slot.item() == null) {
        throw new IllegalArgumentException("entity equipment lists only non-empty slots");
      }
      if (!positions.add(slot.slot())) {
        throw new IllegalArgumentException("equipment contains duplicate slot " + slot.slot());
      }
    }
  }

  private static void validateEffects(
      List<StatusEffectInfo> activeEffects, boolean activeEffectsTruncated) {
    if (activeEffects.size() > MAX_ACTIVE_EFFECTS) {
      throw new IllegalArgumentException("activeEffects exceeds " + MAX_ACTIVE_EFFECTS);
    }
    Set<String> effectIds = new HashSet<>();
    for (StatusEffectInfo effect : activeEffects) {
      if (!effectIds.add(effect.effectId())) {
        throw new IllegalArgumentException(
            "activeEffects contains duplicate effect " + effect.effectId());
      }
    }
    if (activeEffectsTruncated && activeEffects.size() != MAX_ACTIVE_EFFECTS) {
      throw new IllegalArgumentException(
          "truncated activeEffects must contain the maximum serialized entries");
    }
  }
}
