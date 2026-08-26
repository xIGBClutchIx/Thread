package me.clutchy.thread.platform.minecraft.world;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.world.BlockEntityInfo;
import me.clutchy.thread.core.model.world.BlockInfo;
import me.clutchy.thread.core.model.world.BlockPosition;
import me.clutchy.thread.core.model.world.ContainerInspectionQuery;
import me.clutchy.thread.core.model.world.EntityInfo;
import me.clutchy.thread.core.model.world.NearbyContainerQuery;
import me.clutchy.thread.core.model.world.NearbyContainerResult;
import me.clutchy.thread.core.model.world.NearbyContainerSummary;
import me.clutchy.thread.core.model.world.NearbyEntityQuery;
import me.clutchy.thread.core.model.world.NearbyEntityResult;
import me.clutchy.thread.core.model.world.Position;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.minecraft.game.MinecraftProviderLimits;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** Loader-neutral bounded queries over entities already present in the client level. */
public final class MinecraftWorldProvider implements WorldProvider {
  private static final EntityTypeTest<Entity, Entity> ALL_ENTITIES =
      EntityTypeTest.forClass(Entity.class);

  private final Minecraft client;
  private final GameThreadExecutor clientThread;
  private final MinecraftSessionGuard sessionGuard;
  private final MinecraftProviderLimits limits;
  private final MinecraftDtoMapper mapper;
  private final MinecraftEntityEnricherRegistry entityEnrichers;
  private final MinecraftBlockEntityInspectorRegistry blockEntityInspectors;
  private final MinecraftBlockEnricherRegistry blockEnrichers;
  private final Duration gameThreadTimeout;

  public MinecraftWorldProvider(
      Minecraft client,
      GameThreadExecutor clientThread,
      MinecraftSessionGuard sessionGuard,
      MinecraftProviderLimits limits,
      MinecraftDtoMapper mapper,
      MinecraftEntityEnricherRegistry entityEnrichers,
      MinecraftBlockEntityInspectorRegistry blockEntityInspectors,
      MinecraftBlockEnricherRegistry blockEnrichers,
      Duration gameThreadTimeout) {
    this.client = Objects.requireNonNull(client, "client");
    this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
    this.sessionGuard = Objects.requireNonNull(sessionGuard, "sessionGuard");
    this.limits = Objects.requireNonNull(limits, "limits");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.entityEnrichers = Objects.requireNonNull(entityEnrichers, "entityEnrichers");
    this.blockEntityInspectors =
        Objects.requireNonNull(blockEntityInspectors, "blockEntityInspectors");
    this.blockEnrichers = Objects.requireNonNull(blockEnrichers, "blockEnrichers");
    this.gameThreadTimeout = Objects.requireNonNull(gameThreadTimeout, "gameThreadTimeout");
  }

  @Override
  public ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query) {
    Objects.requireNonNull(query, "query");
    Optional<ToolError> invalidQuery = validateQuery(query, limits);
    if (invalidQuery.isPresent()) {
      return ToolResult.failure(invalidQuery.orElseThrow());
    }
    return MinecraftProviderSupport.read(
        clientThread, "world.nearby_entities", () -> readNearbyEntities(query));
  }

  @Override
  public ToolResult<NearbyContainerResult> nearbyContainers(NearbyContainerQuery query) {
    Objects.requireNonNull(query, "query");
    Optional<ToolError> invalidQuery = validateContainerQuery(query, limits);
    if (invalidQuery.isPresent()) {
      return ToolResult.failure(invalidQuery.orElseThrow());
    }
    ToolResult<WorldContext> captured = captureWorld("world.nearby_containers.capture");
    if (!captured.successful()) {
      return ToolResult.failure(Objects.requireNonNull(captured.error()));
    }
    WorldContext context = Objects.requireNonNull(captured.value());
    return inspectOnServer(
        context,
        "world.nearby_containers.inspect",
        access ->
            ToolResult.success(findNearbyContainers(query, context.playerPosition(), access)));
  }

  @Override
  public ToolResult<BlockInfo> inspectContainer(ContainerInspectionQuery query) {
    Objects.requireNonNull(query, "query");
    ToolResult<WorldContext> captured = captureWorld("world.inspect_container.capture");
    if (!captured.successful()) {
      return ToolResult.failure(Objects.requireNonNull(captured.error()));
    }
    WorldContext context = Objects.requireNonNull(captured.value());
    double distance = distance(context.playerPosition(), query.position());
    if (distance > limits.maxContainerRadius()) {
      return ToolResult.failure(
          new ToolError(
              ToolErrorCode.OUT_OF_RANGE,
              "The requested container position is outside the supported inspection range.",
              false,
              Map.of("maximum", Double.toString(limits.maxContainerRadius()))));
    }
    return inspectOnServer(
        context,
        "world.inspect_container.inspect",
        access -> inspectLoadedContainer(query.position(), distance, access));
  }

  static Optional<ToolError> validateQuery(
      NearbyEntityQuery query, MinecraftProviderLimits limits) {
    if (query.radius() > limits.maxEntityRadius()) {
      return Optional.of(
          new ToolError(
              ToolErrorCode.OUT_OF_RANGE,
              "Requested entity radius exceeds the server-side maximum.",
              false,
              Map.of("maximum", Double.toString(limits.maxEntityRadius()))));
    }
    if (query.limit() > limits.maxEntityResults()) {
      return Optional.of(
          new ToolError(
              ToolErrorCode.RESULT_LIMIT_EXCEEDED,
              "Requested entity result limit exceeds the server-side maximum.",
              false,
              Map.of("maximum", Integer.toString(limits.maxEntityResults()))));
    }
    return Optional.empty();
  }

  static Optional<ToolError> validateContainerQuery(
      NearbyContainerQuery query, MinecraftProviderLimits limits) {
    if (query.radius() > limits.maxContainerRadius()) {
      return Optional.of(
          new ToolError(
              ToolErrorCode.OUT_OF_RANGE,
              "Requested container radius exceeds the server-side maximum.",
              false,
              Map.of("maximum", Double.toString(limits.maxContainerRadius()))));
    }
    if (query.limit() > limits.maxContainerResults()) {
      return Optional.of(
          new ToolError(
              ToolErrorCode.RESULT_LIMIT_EXCEEDED,
              "Requested container result limit exceeds the server-side maximum.",
              false,
              Map.of("maximum", Integer.toString(limits.maxContainerResults()))));
    }
    return Optional.empty();
  }

  static NearbyContainerResult findNearbyContainers(
      NearbyContainerQuery query, Position playerPosition, LoadedContainerAccess access) {
    List<NearbyPosition> positions = nearbyPositions(playerPosition, query.radius());
    List<NearbyContainerSummary> containers = new ArrayList<>(query.limit() + 1);
    for (NearbyPosition candidate : positions) {
      if (!access.loaded(candidate.position())) {
        continue;
      }
      access
          .inspect(candidate.position(), candidate.distance())
          .map(MinecraftWorldProvider::summary)
          .ifPresent(containers::add);
      if (containers.size() > query.limit()) {
        break;
      }
    }
    boolean truncated = containers.size() > query.limit();
    return new NearbyContainerResult(
        query.radius(),
        query.limit(),
        truncated,
        containers.stream().limit(query.limit()).toList());
  }

  static ToolResult<BlockInfo> inspectLoadedContainer(
      BlockPosition position, double distance, LoadedContainerAccess access) {
    if (!access.loaded(position)) {
      return ToolResult.failure(
          ToolError.of(
              ToolErrorCode.NOT_AVAILABLE,
              "The requested container position is not currently loaded.",
              true));
    }
    return access
        .inspect(position, distance)
        .map(ToolResult::success)
        .orElseGet(
            () ->
                ToolResult.failure(
                    ToolError.of(
                        ToolErrorCode.NOT_FOUND,
                        "The requested position is not a supported container.",
                        false)));
  }

  private ToolResult<NearbyEntityResult> readNearbyEntities(NearbyEntityQuery query) {
    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }
    LocalPlayer player = client.player;
    double radiusSquared = query.radius() * query.radius();
    AABB bounds = player.getBoundingBox().inflate(query.radius());
    List<Entity> loadedEntities = new ArrayList<>(query.limit() + 1);

    // This overload reads the client level's existing entity index and stops after limit + 1. It
    // neither asks the chunk source for missing chunks nor permits an unbounded result scan.
    client.level.getEntities(
        ALL_ENTITIES,
        bounds,
        entity -> entity != player && entity.distanceToSqr(player) <= radiusSquared,
        loadedEntities,
        query.limit() + 1);

    boolean truncated = loadedEntities.size() > query.limit();
    List<EntityInfo> entities =
        loadedEntities.stream()
            .limit(query.limit())
            .map(
                entity ->
                    entityEnrichers.enrich(
                        entity, mapper.entity(entity, Math.sqrt(entity.distanceToSqr(player)))))
            .toList();
    return ToolResult.success(
        new NearbyEntityResult(query.radius(), query.limit(), truncated, entities));
  }

  private ToolResult<WorldContext> captureWorld(String operation) {
    return MinecraftProviderSupport.read(clientThread, operation, this::readWorldContext);
  }

  private ToolResult<WorldContext> readWorldContext() {
    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }
    LocalPlayer player = client.player;
    return ToolResult.success(
        new WorldContext(
            Objects.requireNonNull(client.getSingleplayerServer()),
            Objects.requireNonNull(client.level).dimension(),
            new Position(player.getX(), player.getY(), player.getZ())));
  }

  private <T> ToolResult<T> inspectOnServer(
      WorldContext context,
      String operation,
      java.util.function.Function<LoadedContainerAccess, ToolResult<T>> inspection) {
    GameThreadExecutor serverThread =
        MinecraftThreadExecutor.forServer(context.server(), gameThreadTimeout);
    return MinecraftProviderSupport.read(
        serverThread,
        operation,
        () -> {
          ServerLevel level = context.server().getLevel(context.dimension());
          if (level == null) {
            return ToolResult.failure(
                ToolError.of(
                    ToolErrorCode.NOT_AVAILABLE,
                    "The player's world is no longer available.",
                    true));
          }
          return inspection.apply(new ServerContainerAccess(level));
        });
  }

  private final class ServerContainerAccess implements LoadedContainerAccess {
    private final ServerLevel level;

    private ServerContainerAccess(ServerLevel level) {
      this.level = level;
    }

    @Override
    public boolean loaded(BlockPosition position) {
      return level.getChunkSource().hasChunk(position.x() >> 4, position.z() >> 4);
    }

    @Override
    public Optional<BlockInfo> inspect(BlockPosition position, double distance) {
      BlockPos minecraftPosition = new BlockPos(position.x(), position.y(), position.z());
      BlockEntity blockEntity = level.getBlockEntity(minecraftPosition);
      if (blockEntity == null) {
        return Optional.empty();
      }
      Optional<BlockEntityInfo> inspection = blockEntityInspectors.inspectContainer(blockEntity);
      if (inspection.isEmpty()) {
        return Optional.empty();
      }
      BlockState state = level.getBlockState(minecraftPosition);
      BlockInfo base = mapper.block(state, minecraftPosition, distance, inspection.orElseThrow());
      return Optional.of(blockEnrichers.enrich(level, minecraftPosition, state, blockEntity, base));
    }
  }

  private static List<NearbyPosition> nearbyPositions(Position origin, double radius) {
    int minimumX = (int) Math.floor(origin.x() - radius);
    int maximumX = (int) Math.floor(origin.x() + radius);
    int minimumY = (int) Math.floor(origin.y() - radius);
    int maximumY = (int) Math.floor(origin.y() + radius);
    int minimumZ = (int) Math.floor(origin.z() - radius);
    int maximumZ = (int) Math.floor(origin.z() + radius);
    double radiusSquared = radius * radius;
    List<NearbyPosition> positions = new ArrayList<>();
    for (int x = minimumX; x <= maximumX; x++) {
      for (int y = minimumY; y <= maximumY; y++) {
        for (int z = minimumZ; z <= maximumZ; z++) {
          BlockPosition position = new BlockPosition(x, y, z);
          double distance = distance(origin, position);
          if (distance * distance <= radiusSquared) {
            positions.add(new NearbyPosition(position, distance));
          }
        }
      }
    }
    return positions.stream()
        .sorted(
            Comparator.comparingDouble(NearbyPosition::distance)
                .thenComparingInt(candidate -> candidate.position().x())
                .thenComparingInt(candidate -> candidate.position().y())
                .thenComparingInt(candidate -> candidate.position().z()))
        .toList();
  }

  private static NearbyContainerSummary summary(BlockInfo block) {
    BlockEntityInfo container = Objects.requireNonNull(block.blockEntity());
    boolean contentsKnown =
        !"false".equals(container.state().get("contentsResolved"))
            && !"true".equals(container.state().get("itemsTruncated"));
    Integer usedSlots = contentsKnown ? container.items().size() : null;
    List<me.clutchy.thread.core.model.world.BlockEntityItemInfo> itemSummary =
        container.items().stream().limit(NearbyContainerSummary.MAX_SUMMARY_ITEMS).toList();
    return new NearbyContainerSummary(
        block.blockId(),
        container.typeId(),
        block.displayName(),
        block.position(),
        block.distance(),
        container.inventorySize(),
        usedSlots,
        itemSummary,
        usedSlots != null && usedSlots > itemSummary.size());
  }

  private static double distance(Position origin, BlockPosition position) {
    double x = position.x() + 0.5D - origin.x();
    double y = position.y() + 0.5D - origin.y();
    double z = position.z() + 0.5D - origin.z();
    return Math.sqrt(x * x + y * y + z * z);
  }

  interface LoadedContainerAccess {
    boolean loaded(BlockPosition position);

    Optional<BlockInfo> inspect(BlockPosition position, double distance);
  }

  private record NearbyPosition(BlockPosition position, double distance) {}

  private record WorldContext(
      MinecraftServer server, ResourceKey<Level> dimension, Position playerPosition) {}
}
