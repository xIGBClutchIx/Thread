package me.clutchy.thread.platform.minecraft.mapping;

import java.util.List;
import me.clutchy.thread.core.model.item.ItemComponentsInfo;
import me.clutchy.thread.core.model.item.ItemDurabilityInfo;
import me.clutchy.thread.core.model.item.ItemEnchantmentInfo;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.world.EntityClassification;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;

/** Converts Minecraft runtime objects into detached Thread DTOs at the platform boundary. */
public final class MinecraftDtoMapper {
  private static final int MAX_TEXT_LENGTH = 256;

  /** Converts a non-empty Minecraft item stack. */
  public ItemStackInfo itemStack(ItemStack stack) {
    if (stack.isEmpty()) {
      throw new IllegalArgumentException("cannot map an empty item stack");
    }
    return new ItemStackInfo(
        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
        plainText(stack.getItemName()),
        stack.getCustomName() == null ? null : plainText(stack.getCustomName()),
        stack.getCount(),
        stack.getMaxStackSize(),
        durability(stack),
        enchantments(stack),
        selectedComponents(stack));
  }

  /** Converts an equipment stack, using null for an empty equipment position. */
  public ItemStackInfo optionalItemStack(ItemStack stack) {
    return stack.isEmpty() ? null : itemStack(stack);
  }

  /** Converts an entity's current continuous position. */
  public Position position(Entity entity) {
    return new Position(entity.getX(), entity.getY(), entity.getZ());
  }

  /** Converts an already-loaded entity with health and supported Minecraft classifications. */
  public EntityInfo entity(Entity entity, double distance) {
    boolean living = entity instanceof LivingEntity;
    Double health = living ? (double) ((LivingEntity) entity).getHealth() : null;
    Double maxHealth = living ? (double) ((LivingEntity) entity).getMaxHealth() : null;
    return new EntityInfo(
        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
        plainText(entity.getType().getDescription()),
        entity.getCustomName() == null ? null : plainText(entity.getCustomName()),
        distance,
        position(entity),
        living,
        health,
        maxHealth,
        classification(entity));
  }

  static EntityClassification classification(Entity entity) {
    if (!(entity instanceof LivingEntity)) {
      return null;
    }
    if (entity instanceof NeutralMob) {
      return EntityClassification.NEUTRAL;
    }
    if (entity instanceof Enemy) {
      return EntityClassification.HOSTILE;
    }
    if (entity instanceof Animal
        || entity instanceof AmbientCreature
        || entity instanceof WaterAnimal
        || entity instanceof AbstractVillager) {
      return EntityClassification.PASSIVE;
    }
    return null;
  }

  private static ItemDurabilityInfo durability(ItemStack stack) {
    if (!stack.isDamageableItem()) {
      return null;
    }
    int maximum = stack.getMaxDamage();
    int damage = Math.max(0, Math.min(stack.getDamageValue(), maximum));
    return new ItemDurabilityInfo(maximum - damage, maximum, damage);
  }

  private static List<ItemEnchantmentInfo> enchantments(ItemStack stack) {
    return stack.getEnchantments().entrySet().stream()
        .flatMap(
            entry ->
                entry.getKey().unwrapKey().stream()
                    .map(
                        key ->
                            new ItemEnchantmentInfo(
                                key.identifier().toString(), entry.getIntValue())))
        .toList();
  }

  private static ItemComponentsInfo selectedComponents(ItemStack stack) {
    String rarity =
        stack.getRarity() == Rarity.COMMON ? null : stack.getRarity().getSerializedName();
    boolean unbreakable = stack.has(DataComponents.UNBREAKABLE);
    int repairCost = stack.getOrDefault(DataComponents.REPAIR_COST, 0);
    ItemLore loreComponent = stack.get(DataComponents.LORE);
    List<String> lore =
        loreComponent == null
            ? List.of()
            : loreComponent.lines().stream()
                .limit(ItemComponentsInfo.MAX_LORE_LINES)
                .map(MinecraftDtoMapper::plainText)
                .filter(line -> !line.isBlank())
                .toList();
    PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
    String potionId =
        potion == null
            ? null
            : potion
                .potion()
                .flatMap(holder -> holder.unwrapKey())
                .map(key -> key.identifier().toString())
                .orElse(null);
    ItemContainerContents container = stack.get(DataComponents.CONTAINER);
    int storedItemStacks =
        container == null ? 0 : Math.toIntExact(container.nonEmptyItemCopyStream().count());
    if (rarity == null
        && !unbreakable
        && repairCost == 0
        && lore.isEmpty()
        && potionId == null
        && storedItemStacks == 0) {
      return null;
    }
    return new ItemComponentsInfo(
        rarity, unbreakable, repairCost, lore, potionId, storedItemStacks);
  }

  private static String plainText(Component component) {
    String value = component.getString();
    int codePoints = value.codePointCount(0, value.length());
    if (codePoints <= MAX_TEXT_LENGTH) {
      return value;
    }
    int end = value.offsetByCodePoints(0, MAX_TEXT_LENGTH);
    return value.substring(0, end);
  }
}
