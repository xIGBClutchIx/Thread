package me.clutchy.thread.platform.fabric.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.EntityInfo;
import me.clutchy.thread.core.model.NearbyEntityQuery;
import me.clutchy.thread.core.model.NearbyEntityResult;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.provider.WorldProvider;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.fabric.game.FabricProviderLimits;
import me.clutchy.thread.platform.fabric.game.FabricProviderSupport;
import me.clutchy.thread.platform.fabric.game.FabricSessionGuard;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** Fabric-backed bounded queries over entities already present in the client level. */
public final class FabricWorldProvider implements WorldProvider {
  private static final EntityTypeTest<Entity, Entity> ALL_ENTITIES =
      EntityTypeTest.forClass(Entity.class);

  private final Minecraft client;
  private final GameThreadExecutor clientThread;
  private final FabricSessionGuard sessionGuard;
  private final FabricProviderLimits limits;
  private final FabricDtoMapper mapper;

  public FabricWorldProvider(
      Minecraft client,
      GameThreadExecutor clientThread,
      FabricSessionGuard sessionGuard,
      FabricProviderLimits limits,
      FabricDtoMapper mapper) {
    this.client = Objects.requireNonNull(client, "client");
    this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
    this.sessionGuard = Objects.requireNonNull(sessionGuard, "sessionGuard");
    this.limits = Objects.requireNonNull(limits, "limits");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query) {
    Objects.requireNonNull(query, "query");
    Optional<ToolError> invalidQuery = validateQuery(query, limits);
    if (invalidQuery.isPresent()) {
      return ToolResult.failure(invalidQuery.orElseThrow());
    }
    return FabricProviderSupport.read(
        clientThread, "world.nearby_entities", () -> readNearbyEntities(query));
  }

  static Optional<ToolError> validateQuery(NearbyEntityQuery query, FabricProviderLimits limits) {
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
            .map(entity -> mapper.entity(entity, Math.sqrt(entity.distanceToSqr(player))))
            .toList();
    return ToolResult.success(
        new NearbyEntityResult(query.radius(), query.limit(), truncated, entities));
  }
}
