package me.clutchy.thread.platform.fabric.player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.player.EquipmentPosition;
import me.clutchy.thread.core.model.player.EquipmentSlotInfo;
import me.clutchy.thread.core.model.player.EquipmentSnapshot;
import me.clutchy.thread.core.model.player.InventorySlotInfo;
import me.clutchy.thread.core.model.player.InventorySnapshot;
import me.clutchy.thread.core.model.player.PlayerStatus;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.fabric.game.FabricProviderSupport;
import me.clutchy.thread.platform.fabric.game.FabricSessionGuard;
import me.clutchy.thread.platform.fabric.inspection.FabricBlockEntityInspectorRegistry;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import me.clutchy.thread.platform.fabric.threading.MinecraftThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
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
  private final FabricBlockEntityInspectorRegistry blockEntityInspectors;
  private final Duration gameThreadTimeout;

  public FabricPlayerProvider(
      Minecraft client,
      GameThreadExecutor clientThread,
      FabricSessionGuard sessionGuard,
      FabricDtoMapper mapper,
      FabricBlockEntityInspectorRegistry blockEntityInspectors,
      Duration gameThreadTimeout) {
    this.client = Objects.requireNonNull(client, "client");
    this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
    this.sessionGuard = Objects.requireNonNull(sessionGuard, "sessionGuard");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.blockEntityInspectors =
        Objects.requireNonNull(blockEntityInspectors, "blockEntityInspectors");
    this.gameThreadTimeout = Objects.requireNonNull(gameThreadTimeout, "gameThreadTimeout");
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
    ToolResult<Optional<TargetBlockContext>> captured =
        FabricProviderSupport.read(
            clientThread, "player.target_block.capture", this::captureTargetBlock);
    if (!captured.successful()) {
      return ToolResult.failure(Objects.requireNonNull(captured.error()));
    }
    Optional<TargetBlockContext> target = Objects.requireNonNull(captured.value());
    if (target.isEmpty()) {
      return ToolResult.success(Optional.empty());
    }
    TargetBlockContext context = target.orElseThrow();
    GameThreadExecutor serverThread =
        MinecraftThreadExecutor.forServer(context.server(), gameThreadTimeout);
    return FabricProviderSupport.read(
        serverThread, "player.target_block.inspect", () -> inspectTargetBlock(context));
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
    for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
      if (!inventory.getNonEquipmentItems().get(slot).isEmpty()) {
        slots.add(
            new InventorySlotInfo(
                slot, mapper.itemStack(inventory.getNonEquipmentItems().get(slot))));
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
    List<EquipmentSlotInfo> slots =
        List.of(
            equipmentSlot(EquipmentPosition.MAIN_HAND, player.getMainHandItem()),
            equipmentSlot(EquipmentPosition.OFF_HAND, player.getOffhandItem()),
            equipmentSlot(EquipmentPosition.HEAD, player.getItemBySlot(EquipmentSlot.HEAD)),
            equipmentSlot(EquipmentPosition.CHEST, player.getItemBySlot(EquipmentSlot.CHEST)),
            equipmentSlot(EquipmentPosition.LEGS, player.getItemBySlot(EquipmentSlot.LEGS)),
            equipmentSlot(EquipmentPosition.FEET, player.getItemBySlot(EquipmentSlot.FEET)));
    return ToolResult.success(new EquipmentSnapshot(slots));
  }

  private ToolResult<Optional<TargetBlockContext>> captureTargetBlock() {
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
    double distance = client.player.getEyePosition().distanceTo(blockHit.getLocation());
    return ToolResult.success(
        Optional.of(
            new TargetBlockContext(
                Objects.requireNonNull(client.getSingleplayerServer()),
                Objects.requireNonNull(client.level).dimension(),
                position.immutable(),
                distance)));
  }

  private ToolResult<Optional<BlockInfo>> inspectTargetBlock(TargetBlockContext context) {
    ServerLevel level = context.server().getLevel(context.dimension());
    if (level == null) {
      return targetUnavailable("The targeted block's world is no longer available.");
    }
    BlockPos position = context.position();
    if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)) {
      return targetUnavailable("The targeted block's chunk is no longer loaded.");
    }
    BlockState state = level.getBlockState(position);
    var blockEntity = level.getBlockEntity(position);
    return ToolResult.success(
        Optional.of(
            new BlockInfo(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                state.getBlock().getName().getString(),
                new BlockPosition(position.getX(), position.getY(), position.getZ()),
                properties(state),
                context.distance(),
                blockEntity != null,
                blockEntity == null ? null : blockEntityInspectors.inspect(blockEntity))));
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

  private EquipmentSlotInfo equipmentSlot(
      EquipmentPosition position, net.minecraft.world.item.ItemStack stack) {
    return new EquipmentSlotInfo(position, mapper.optionalItemStack(stack));
  }

  private static ToolResult<Optional<BlockInfo>> targetUnavailable(String message) {
    return ToolResult.failure(ToolError.of(ToolErrorCode.NOT_AVAILABLE, message, true));
  }

  private record TargetBlockContext(
      MinecraftServer server, ResourceKey<Level> dimension, BlockPos position, double distance) {}
}
