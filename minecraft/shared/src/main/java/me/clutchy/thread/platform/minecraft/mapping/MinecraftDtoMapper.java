package me.clutchy.thread.platform.minecraft.mapping;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.StreamSupport;
import me.clutchy.thread.core.model.item.ItemComponentsInfo;
import me.clutchy.thread.core.model.item.ItemDurabilityInfo;
import me.clutchy.thread.core.model.item.ItemEnchantmentInfo;
import me.clutchy.thread.core.model.item.ItemStackInfo;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.player.EquipmentSlotInfo;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.EntityAgeState;
import me.clutchy.thread.core.model.world.EntityClassification;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.Position;
import me.clutchy.thread.core.model.world.StatusEffectInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

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

  /** Converts one direct vehicle without expanding it into a full entity inspection payload. */
  public PlayerStatus.Vehicle vehicle(Entity entity) {
    return new PlayerStatus.Vehicle(
        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
        plainText(entity.getType().getDescription()),
        entity.getCustomName() == null ? null : plainText(entity.getCustomName()));
  }

  /** Converts a loaded block and optional selected block-entity state into a detached snapshot. */
  public BlockInfo block(
      BlockState state,
      net.minecraft.core.BlockPos position,
      double distance,
      BlockEntityInfo blockEntity) {
    return new BlockInfo(
        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
        state.getBlock().getName().getString(),
        new BlockPosition(position.getX(), position.getY(), position.getZ()),
        properties(state),
        distance,
        blockEntity != null,
        blockEntity);
  }

  /** Converts an already-loaded entity with health and supported Minecraft classifications. */
  public EntityInfo entity(Entity entity, double distance) {
    LivingEntity livingEntity = entity instanceof LivingEntity living ? living : null;
    List<StatusEffectInfo> activeEffects =
        livingEntity == null ? List.of() : activeEffects(livingEntity);
    TameMetadata tame = tameMetadata(entity);
    VillagerMetadata villager = villagerMetadata(entity);
    return new EntityInfo(
        BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
        plainText(entity.getType().getDescription()),
        entity.getCustomName() == null ? null : plainText(entity.getCustomName()),
        distance,
        position(entity),
        livingEntity != null,
        livingEntity == null ? null : (double) livingEntity.getHealth(),
        livingEntity == null ? null : (double) livingEntity.getMaxHealth(),
        classification(entity),
        livingEntity == null ? List.of() : equipment(livingEntity),
        activeEffects.stream().limit(EntityInfo.MAX_ACTIVE_EFFECTS).toList(),
        activeEffects.size() > EntityInfo.MAX_ACTIVE_EFFECTS,
        ageState(entity, livingEntity),
        tame.tamed(),
        tame.ownerName(),
        villager.profession(),
        villager.level());
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

  private List<EquipmentSlotInfo> equipment(LivingEntity entity) {
    return java.util.stream.Stream.of(
            equipmentSlot(EquipmentPosition.MAIN_HAND, entity, EquipmentSlot.MAINHAND),
            equipmentSlot(EquipmentPosition.OFF_HAND, entity, EquipmentSlot.OFFHAND),
            equipmentSlot(EquipmentPosition.HEAD, entity, EquipmentSlot.HEAD),
            equipmentSlot(EquipmentPosition.CHEST, entity, EquipmentSlot.CHEST),
            equipmentSlot(EquipmentPosition.LEGS, entity, EquipmentSlot.LEGS),
            equipmentSlot(EquipmentPosition.FEET, entity, EquipmentSlot.FEET))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private EquipmentSlotInfo equipmentSlot(
      EquipmentPosition position, LivingEntity entity, EquipmentSlot slot) {
    ItemStack stack = entity.getItemBySlot(slot);
    return stack.isEmpty() ? null : new EquipmentSlotInfo(position, itemStack(stack));
  }

  /** Converts active effects into stable registry-ID order for shared entity/player payloads. */
  public List<StatusEffectInfo> activeEffects(LivingEntity entity) {
    return entity.getActiveEffects().stream()
        .filter(effect -> effect.getEffect().unwrapKey().isPresent())
        .sorted(
            java.util.Comparator.comparing(
                effect -> effect.getEffect().unwrapKey().orElseThrow().identifier().toString()))
        .map(MinecraftDtoMapper::statusEffect)
        .toList();
  }

  private static StatusEffectInfo statusEffect(MobEffectInstance effect) {
    String effectId = effect.getEffect().unwrapKey().orElseThrow().identifier().toString();
    return new StatusEffectInfo(
        effectId,
        plainText(effect.getEffect().value().getDisplayName()),
        effect.getAmplifier(),
        effect.isInfiniteDuration() ? null : Math.max(0, effect.getDuration()),
        effect.isInfiniteDuration(),
        effect.isAmbient(),
        effect.isVisible(),
        effect.showIcon());
  }

  private static TameMetadata tameMetadata(Entity entity) {
    Boolean tamed = null;
    if (entity instanceof TamableAnimal tamable) {
      tamed = tamable.isTame();
    } else if (entity instanceof AbstractHorse horse) {
      tamed = horse.isTamed();
    }
    if (tamed == null) {
      return TameMetadata.NOT_APPLICABLE;
    }
    String ownerName = null;
    if (tamed && entity instanceof OwnableEntity ownable) {
      LivingEntity owner = ownable.getOwner();
      ownerName = owner == null ? null : plainText(owner.getName());
    }
    return new TameMetadata(tamed, ownerName);
  }

  private static VillagerMetadata villagerMetadata(Entity entity) {
    if (!(entity instanceof Villager villager)) {
      return VillagerMetadata.NOT_APPLICABLE;
    }
    VillagerData data = villager.getVillagerData();
    String profession =
        data.profession()
            .unwrapKey()
            .map(key -> key.identifier().toString())
            .orElse("minecraft:none");
    return new VillagerMetadata(profession, data.level());
  }

  private static EntityAgeState ageState(Entity entity, LivingEntity living) {
    if (living == null
        || !(entity instanceof net.minecraft.world.entity.AgeableMob
            || entity instanceof Zombie
            || entity instanceof Piglin
            || entity instanceof Zoglin
            || entity instanceof ArmorStand)) {
      return null;
    }
    return living.isBaby() ? EntityAgeState.BABY : EntityAgeState.ADULT;
  }

  private static ItemDurabilityInfo durability(ItemStack stack) {
    if (!stack.isDamageableItem()) {
      return null;
    }
    int maximum = stack.getMaxDamage();
    int damage = Math.max(0, Math.min(stack.getDamageValue(), maximum));
    return new ItemDurabilityInfo(maximum - damage, maximum, damage);
  }

  private static Map<String, String> properties(BlockState state) {
    Map<String, String> properties = new TreeMap<>();
    for (Property<?> property : state.getProperties()) {
      properties.put(property.getName(), propertyValue(state, property));
    }
    return properties;
  }

  private static <T extends Comparable<T>> String propertyValue(
      BlockState state, Property<T> property) {
    return property.getName(state.getValue(property));
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
        container == null
            ? 0
            : Math.toIntExact(
                StreamSupport.stream(container.nonEmptyItems().spliterator(), false).count());
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

  private record TameMetadata(Boolean tamed, String ownerName) {
    private static final TameMetadata NOT_APPLICABLE = new TameMetadata(null, null);
  }

  private record VillagerMetadata(String profession, Integer level) {
    private static final VillagerMetadata NOT_APPLICABLE = new VillagerMetadata(null, null);
  }
}
