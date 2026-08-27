package me.clutchy.thread.platform.minecraft.player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
import me.clutchy.thread.core.model.world.StatusEffectInfo;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.provider.PlayerProvider;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.minecraft.game.MinecraftProviderSupport;
import me.clutchy.thread.platform.minecraft.game.MinecraftSessionGuard;
import me.clutchy.thread.platform.minecraft.inspection.MinecraftBlockEnricherRegistry;
import me.clutchy.thread.platform.minecraft.inspection.MinecraftBlockEntityInspectorRegistry;
import me.clutchy.thread.platform.minecraft.mapping.MinecraftDtoMapper;
import me.clutchy.thread.platform.minecraft.threading.MinecraftThreadExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Loader-neutral snapshots of client-owned local-player and camera-target state. */
public final class MinecraftPlayerProvider implements PlayerProvider {
  private final Minecraft client;
  private final GameThreadExecutor clientThread;
  private final MinecraftSessionGuard sessionGuard;
  private final MinecraftDtoMapper mapper;
  private final MinecraftBlockEntityInspectorRegistry blockEntityInspectors;
  private final MinecraftBlockEnricherRegistry blockEnrichers;
  private final Duration gameThreadTimeout;

  public MinecraftPlayerProvider(
      Minecraft client,
      GameThreadExecutor clientThread,
      MinecraftSessionGuard sessionGuard,
      MinecraftDtoMapper mapper,
      MinecraftBlockEntityInspectorRegistry blockEntityInspectors,
      MinecraftBlockEnricherRegistry blockEnrichers,
      Duration gameThreadTimeout) {
    this.client = Objects.requireNonNull(client, "client");
    this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
    this.sessionGuard = Objects.requireNonNull(sessionGuard, "sessionGuard");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.blockEntityInspectors =
        Objects.requireNonNull(blockEntityInspectors, "blockEntityInspectors");
    this.blockEnrichers = Objects.requireNonNull(blockEnrichers, "blockEnrichers");
    this.gameThreadTimeout = Objects.requireNonNull(gameThreadTimeout, "gameThreadTimeout");
  }

  @Override
  public ToolResult<PlayerStatus> status() {
    ToolResult<PlayerStatusContext> captured =
        MinecraftProviderSupport.read(
            clientThread, "player.status.capture", this::captureStatusContext);
    if (!captured.successful()) {
      return ToolResult.failure(Objects.requireNonNull(captured.error()));
    }
    PlayerStatusContext context = Objects.requireNonNull(captured.value());
    GameThreadExecutor serverThread =
        MinecraftThreadExecutor.forServer(context.server(), gameThreadTimeout);
    return MinecraftProviderSupport.read(
        serverThread, "player.status.read", () -> readStatus(context));
  }

  @Override
  public ToolResult<InventorySnapshot> inventory() {
    return MinecraftProviderSupport.read(clientThread, "player.inventory", this::readInventory);
  }

  @Override
  public ToolResult<EquipmentSnapshot> equipment() {
    return MinecraftProviderSupport.read(clientThread, "player.equipment", this::readEquipment);
  }

  @Override
  public ToolResult<Optional<BlockInfo>> targetBlock() {
    ToolResult<Optional<TargetBlockContext>> captured =
        MinecraftProviderSupport.read(
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
    return MinecraftProviderSupport.read(
        serverThread, "player.target_block.inspect", () -> inspectTargetBlock(context));
  }

  private ToolResult<PlayerStatusContext> captureStatusContext() {
    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }
    return ToolResult.success(
        new PlayerStatusContext(
            Objects.requireNonNull(client.getSingleplayerServer()), client.player.getUUID()));
  }

  private ToolResult<PlayerStatus> readStatus(PlayerStatusContext context) {
    ServerPlayer player = context.server().getPlayerList().getPlayer(context.playerId());
    if (player == null) {
      return ToolResult.failure(
          ToolError.of(
              ToolErrorCode.PLAYER_NOT_AVAILABLE,
              "The local player is no longer available.",
              true));
    }
    FoodData food = player.getFoodData();
    List<StatusEffectInfo> activeEffects = mapper.activeEffects(player);
    Entity vehicle = player.getVehicle();
    ServerPlayer.RespawnConfig respawnConfig = player.getRespawnConfig();
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
            player.gameMode().getName(),
            player.level().getLevelData().isHardcore(),
            new PlayerStatus.Armor(
                player.getArmorValue(), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),
            new PlayerStatus.Air(player.getAirSupply(), player.getMaxAirSupply()),
            activeEffects.stream().limit(PlayerStatus.MAX_ACTIVE_EFFECTS).toList(),
            activeEffects.size() > PlayerStatus.MAX_ACTIVE_EFFECTS,
            new PlayerStatus.Movement(
                player.isSprinting(),
                player.isSwimming(),
                player.isCrouching(),
                player.getAbilities().flying,
                player.onGround(),
                player.fallDistance),
            new PlayerStatus.Conditions(
                player.isSleeping(),
                player.isOnFire(),
                player.isFreezing(),
                player.isFullyFrozen()),
            player.getInventory().getSelectedSlot(),
            Math.max(0D, Math.min(1D, player.getAttackStrengthScale(0F))),
            vehicle == null ? null : mapper.vehicle(vehicle),
            respawnConfig == null
                ? null
                : new PlayerStatus.Respawn(
                    respawnConfig.respawnData().dimension().identifier().toString(),
                    new BlockPosition(
                        respawnConfig.respawnData().pos().getX(),
                        respawnConfig.respawnData().pos().getY(),
                        respawnConfig.respawnData().pos().getZ()),
                    respawnConfig.forced())));
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
    BlockInfo base =
        mapper.block(
            state,
            position,
            context.distance(),
            blockEntity == null ? null : blockEntityInspectors.inspect(blockEntity));
    return ToolResult.success(
        Optional.of(blockEnrichers.enrich(level, position, state, blockEntity, base)));
  }

  static boolean isValidBlockTarget(HitResult hitResult) {
    return hitResult instanceof BlockHitResult && hitResult.getType() == HitResult.Type.BLOCK;
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

  private record PlayerStatusContext(MinecraftServer server, UUID playerId) {}
}
