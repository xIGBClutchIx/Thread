package me.clutchy.thread.platform.fabric.inspection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockEntityItemInfo;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationExtensionPoints;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Ordered registry of safe block-entity inspectors used by the Fabric player provider. */
public final class FabricBlockEntityInspectorRegistry {
  private static final System.Logger LOGGER =
      System.getLogger(FabricBlockEntityInspectorRegistry.class.getName());

  private final List<FabricBlockEntityInspector> inspectors = new ArrayList<>();
  private final FabricDtoMapper mapper;
  private final IntegrationExtensionRegistry extensions;

  private FabricBlockEntityInspectorRegistry(
      FabricDtoMapper mapper, IntegrationExtensionRegistry extensions) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.extensions = Objects.requireNonNull(extensions, "extensions");
    inspectors.add(this::inspectFurnace);
    inspectors.add(this::inspectContainer);
  }

  /** Creates the vanilla registry with furnace and generic container inspection. */
  public static FabricBlockEntityInspectorRegistry vanilla(
      FabricDtoMapper mapper, IntegrationExtensionRegistry extensions) {
    return new FabricBlockEntityInspectorRegistry(mapper, extensions);
  }

  /** Returns selected state, falling back to identity-only data for unknown block entities. */
  public BlockEntityInfo inspect(BlockEntity blockEntity) {
    Objects.requireNonNull(blockEntity, "blockEntity");
    for (FabricBlockEntityInspector inspector :
        extensions.contributions(FabricIntegrationExtensionPoints.BLOCK_ENTITY_INSPECTOR)) {
      try {
        Optional<BlockEntityInfo> inspection =
            Objects.requireNonNull(inspector.inspect(blockEntity), "block entity inspection");
        if (inspection.isPresent()) {
          return inspection.orElseThrow();
        }
      } catch (RuntimeException | LinkageError exception) {
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Optional block-entity inspector failed ({0})",
            exception.getClass().getName());
      }
    }
    for (FabricBlockEntityInspector inspector : List.copyOf(inspectors)) {
      Optional<BlockEntityInfo> inspection = inspector.inspect(blockEntity);
      if (inspection.isPresent()) {
        return inspection.orElseThrow();
      }
    }
    return new BlockEntityInfo(typeId(blockEntity), 0, List.of(), Map.of());
  }

  private Optional<BlockEntityInfo> inspectFurnace(BlockEntity blockEntity) {
    if (!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)) {
      return Optional.empty();
    }
    return Optional.of(
        containerInfo(furnace, List.of("input", "fuel", "output"), Map.of("kind", "furnace")));
  }

  private Optional<BlockEntityInfo> inspectContainer(BlockEntity blockEntity) {
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

  private static String typeId(BlockEntity blockEntity) {
    return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString();
  }
}
