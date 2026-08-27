package me.clutchy.thread.platform.minecraft.inspection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockEntityItemInfo;
import me.clutchy.thread.platform.minecraft.integration.MinecraftIntegrationExtensionPoints;
import me.clutchy.thread.platform.minecraft.mapping.MinecraftDtoMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** Ordered registry of safe block-entity inspectors used by the shared player provider. */
public final class MinecraftBlockEntityInspectorRegistry {
  private static final System.Logger LOGGER =
      System.getLogger(MinecraftBlockEntityInspectorRegistry.class.getName());

  private final List<MinecraftBlockEntityInspector> inspectors = new ArrayList<>();
  private final MinecraftDtoMapper mapper;
  private final IntegrationExtensionRegistry extensions;

  private MinecraftBlockEntityInspectorRegistry(
      MinecraftDtoMapper mapper, IntegrationExtensionRegistry extensions) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.extensions = Objects.requireNonNull(extensions, "extensions");
    inspectors.add(this::inspectFurnace);
    inspectors.add(this::inspectBrewingStand);
    inspectors.add(this::inspectVanillaContainer);
  }

  /** Creates the vanilla registry with furnace and generic container inspection. */
  public static MinecraftBlockEntityInspectorRegistry vanilla(
      MinecraftDtoMapper mapper, IntegrationExtensionRegistry extensions) {
    return new MinecraftBlockEntityInspectorRegistry(mapper, extensions);
  }

  /** Returns selected state, falling back to identity-only data for unknown block entities. */
  public BlockEntityInfo inspect(BlockEntity blockEntity) {
    Objects.requireNonNull(blockEntity, "blockEntity");
    return findInspection(blockEntity)
        .orElseGet(() -> new BlockEntityInfo(typeId(blockEntity), 0, List.of(), Map.of()));
  }

  /**
   * Returns safe inventory or machine data only when a built-in or contributed inspector recognizes
   * the block entity as a container-like target.
   */
  public Optional<BlockEntityInfo> inspectContainer(BlockEntity blockEntity) {
    Objects.requireNonNull(blockEntity, "blockEntity");
    return findInspection(blockEntity);
  }

  private Optional<BlockEntityInfo> findInspection(BlockEntity blockEntity) {
    for (MinecraftBlockEntityInspector inspector :
        extensions.contributions(MinecraftIntegrationExtensionPoints.BLOCK_ENTITY_INSPECTOR)) {
      try {
        Optional<BlockEntityInfo> inspection =
            Objects.requireNonNull(inspector.inspect(blockEntity), "block entity inspection");
        if (inspection.isPresent()) {
          return inspection;
        }
      } catch (RuntimeException | LinkageError exception) {
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Optional block-entity inspector failed ({0})",
            exception.getClass().getName());
      }
    }
    for (MinecraftBlockEntityInspector inspector : List.copyOf(inspectors)) {
      Optional<BlockEntityInfo> inspection = inspector.inspect(blockEntity);
      if (inspection.isPresent()) {
        return inspection;
      }
    }
    return Optional.empty();
  }

  private Optional<BlockEntityInfo> inspectFurnace(BlockEntity blockEntity) {
    if (!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)) {
      return Optional.empty();
    }
    return Optional.of(
        containerInfo(furnace, List.of("input", "fuel", "output"), furnaceState(furnace)));
  }

  private Optional<BlockEntityInfo> inspectBrewingStand(BlockEntity blockEntity) {
    if (!(blockEntity instanceof BrewingStandBlockEntity brewingStand)) {
      return Optional.empty();
    }
    return Optional.of(
        containerInfo(
            brewingStand,
            brewingStand,
            List.of("bottle_0", "bottle_1", "bottle_2", "ingredient", "fuel"),
            brewingState(brewingStand)));
  }

  private Optional<BlockEntityInfo> inspectVanillaContainer(BlockEntity blockEntity) {
    if (!(blockEntity instanceof Container container)) {
      return Optional.empty();
    }
    int inspectedSlots = Math.min(container.getContainerSize(), BlockEntityInfo.MAX_ITEMS);
    List<String> slotNames =
        java.util.stream.IntStream.range(0, inspectedSlots).mapToObj(Integer::toString).toList();
    if (container instanceof RandomizableContainer randomizable
        && randomizable.getLootTable() != null) {
      // Reading a slot would unpack the loot table and mutate the world. Report the unresolved
      // identity while leaving contents untouched.
      return Optional.of(
          new BlockEntityInfo(
              typeId(blockEntity),
              container.getContainerSize(),
              List.of(),
              Map.of(
                  "contentsResolved",
                  "false",
                  "lootTable",
                  randomizable.getLootTable().identifier().toString())));
    }
    return Optional.of(containerInfo(blockEntity, container, slotNames, Map.of()));
  }

  private BlockEntityInfo containerInfo(
      AbstractFurnaceBlockEntity furnace, List<String> slotNames, Map<String, String> state) {
    return containerInfo(furnace, furnace, slotNames, state);
  }

  private BlockEntityInfo containerInfo(
      BlockEntity blockEntity,
      Container container,
      List<String> slotNames,
      Map<String, String> state) {
    int inspectedSlots =
        Math.min(
            Math.min(container.getContainerSize(), slotNames.size()), BlockEntityInfo.MAX_ITEMS);
    Map<String, String> boundedState = new TreeMap<>(state);
    if (container.getContainerSize() > inspectedSlots) {
      boundedState.put("inspectedSlots", Integer.toString(inspectedSlots));
      boundedState.put("itemsTruncated", "true");
    }
    List<BlockEntityItemInfo> items = new ArrayList<>();
    for (int slot = 0; slot < inspectedSlots; slot++) {
      if (!container.getItem(slot).isEmpty()) {
        items.add(
            new BlockEntityItemInfo(
                slotNames.get(slot), mapper.itemStack(container.getItem(slot))));
      }
    }
    return new BlockEntityInfo(
        typeId(blockEntity), container.getContainerSize(), items, boundedState);
  }

  private static Map<String, String> furnaceState(AbstractFurnaceBlockEntity furnace) {
    Map<String, String> state = new TreeMap<>();
    state.put("kind", "furnace");
    if (!furnace.hasLevel()) {
      return state;
    }
    // Minecraft has no public read-only progress accessors. Saving into a transient tag is its
    // supported snapshot path; Thread selects four scalar counters and never returns the tag.
    CompoundTag saved = furnace.saveWithoutMetadata(furnace.getLevel().registryAccess());
    state.put("cookingProgress", Short.toString(saved.getShortOr("cooking_time_spent", (short) 0)));
    state.put(
        "cookingTotalTime", Short.toString(saved.getShortOr("cooking_total_time", (short) 0)));
    state.put(
        "litTimeRemaining", Short.toString(saved.getShortOr("lit_time_remaining", (short) 0)));
    state.put("litTotalTime", Short.toString(saved.getShortOr("lit_total_time", (short) 0)));
    return state;
  }

  private static Map<String, String> brewingState(BrewingStandBlockEntity brewingStand) {
    Map<String, String> state = new TreeMap<>();
    state.put("kind", "brewing_stand");
    if (!brewingStand.hasLevel()) {
      return state;
    }
    CompoundTag saved = brewingStand.saveWithoutMetadata(brewingStand.getLevel().registryAccess());
    state.put("brewTime", Short.toString(saved.getShortOr("BrewTime", (short) 0)));
    state.put("fuelUses", Byte.toString(saved.getByteOr("Fuel", (byte) 0)));
    return state;
  }

  private static String typeId(BlockEntity blockEntity) {
    return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString();
  }
}
