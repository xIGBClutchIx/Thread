package me.clutchy.thread.platform.fabric.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.model.BlockInfo;
import me.clutchy.thread.core.model.BlockPosition;
import me.clutchy.thread.core.model.EquipmentSnapshot;
import me.clutchy.thread.core.model.InventorySlotInfo;
import me.clutchy.thread.core.model.InventorySnapshot;
import me.clutchy.thread.core.model.PlayerStatus;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.fabric.game.FabricProviderSupport;
import me.clutchy.thread.platform.fabric.game.FabricSessionGuard;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Fabric-backed snapshots of client-owned local-player and camera-target state. */
public final class FabricPlayerProvider implements PlayerProvider {
  private final Minecraft client;
  private final GameThreadExecutor clientThread;
  private final FabricSessionGuard sessionGuard;
  private final FabricDtoMapper mapper;

  public FabricPlayerProvider(
      Minecraft client,
      GameThreadExecutor clientThread,
      FabricSessionGuard sessionGuard,
      FabricDtoMapper mapper) {
    this.client = Objects.requireNonNull(client, "client");
    this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
    this.sessionGuard = Objects.requireNonNull(sessionGuard, "sessionGuard");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public ToolResult<PlayerStatus> status() {
    return FabricProviderSupport.read(clientThread, "player.status", this::readStatus);
  }

  @Override
  public ToolResult<InventorySnapshot> inventory() {
    return FabricProviderSupport.read(clientThread, "player.inventory", this::readInventory);
  }

  @Override
  public ToolResult<EquipmentSnapshot> equipment() {
    return FabricProviderSupport.read(clientThread, "player.equipment", this::readEquipment);
  }

  @Override
  public ToolResult<Optional<BlockInfo>> targetBlock() {
    return FabricProviderSupport.read(clientThread, "player.target_block", this::readTargetBlock);
  }

  private ToolResult<PlayerStatus> readStatus() {
    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }
    LocalPlayer player = client.player;
    FoodData food = player.getFoodData();
    return ToolResult.success(
        new PlayerStatus(
            player.getHealth(),
            player.getMaxHealth(),
            food.getFoodLevel(),
            food.getSaturationLevel(),
            player.experienceLevel,
            player.experienceProgress,
            mapper.position(player),
            player.level().dimension().identifier().toString(),
            player.gameMode().getName()));
  }

  private ToolResult<InventorySnapshot> readInventory() {
    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }
    Inventory inventory = client.player.getInventory();
    List<InventorySlotInfo> slots = new ArrayList<>();
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      if (!inventory.getItem(slot).isEmpty()) {
        slots.add(new InventorySlotInfo(slot, mapper.itemStack(inventory.getItem(slot))));
      }
    }
    return ToolResult.success(new InventorySnapshot(inventory.getSelectedSlot(), slots));
  }

  private ToolResult<EquipmentSnapshot> readEquipment() {
    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }
    LocalPlayer player = client.player;
    return ToolResult.success(
        new EquipmentSnapshot(
            mapper.optionalItemStack(player.getMainHandItem()),
            mapper.optionalItemStack(player.getOffhandItem()),
            mapper.optionalItemStack(player.getItemBySlot(EquipmentSlot.HEAD)),
            mapper.optionalItemStack(player.getItemBySlot(EquipmentSlot.CHEST)),
            mapper.optionalItemStack(player.getItemBySlot(EquipmentSlot.LEGS)),
            mapper.optionalItemStack(player.getItemBySlot(EquipmentSlot.FEET))));
  }

  private ToolResult<Optional<BlockInfo>> readTargetBlock() {
    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }
    HitResult hitResult = client.hitResult;
    if (!isValidBlockTarget(hitResult)) {
      return ToolResult.success(Optional.empty());
    }
    BlockHitResult blockHit = (BlockHitResult) hitResult;
    BlockPos position = blockHit.getBlockPos();
    BlockState state = client.level.getBlockState(position);
    double distance = client.player.getEyePosition().distanceTo(blockHit.getLocation());
    return ToolResult.success(
        Optional.of(
            new BlockInfo(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                new BlockPosition(position.getX(), position.getY(), position.getZ()),
                properties(state),
                distance)));
  }

  static boolean isValidBlockTarget(HitResult hitResult) {
    return hitResult instanceof BlockHitResult && hitResult.getType() == HitResult.Type.BLOCK;
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
}
