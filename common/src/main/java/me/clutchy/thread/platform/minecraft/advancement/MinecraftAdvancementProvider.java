package me.clutchy.thread.platform.minecraft.advancement;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import me.clutchy.thread.core.error.ToolError;
import me.clutchy.thread.core.error.ToolErrorCode;
import me.clutchy.thread.core.model.advancement.AdvancementCriterionInfo;
import me.clutchy.thread.core.model.advancement.AdvancementDisplayType;
import me.clutchy.thread.core.model.advancement.AdvancementInfo;
import me.clutchy.thread.core.model.advancement.AdvancementSnapshot;
import me.clutchy.thread.core.provider.AdvancementProvider;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.minecraft.game.MinecraftProviderSupport;
import me.clutchy.thread.platform.minecraft.game.MinecraftSessionGuard;
import me.clutchy.thread.platform.minecraft.threading.MinecraftThreadExecutor;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reads visible/known advancement identity from the client tree and progress from the integrated
 * server player.
 *
 * <p>The client tree is authoritative for what Minecraft has exposed to the player. Thread never
 * broadens a request to the server's full advancement registry, which prevents hidden unknown
 * entries from being fabricated into tool results.
 */
public final class MinecraftAdvancementProvider implements AdvancementProvider {
  public static final int MAX_KNOWN_ADVANCEMENTS = 4_096;

  private final Minecraft client;
  private final GameThreadExecutor clientThread;
  private final MinecraftSessionGuard sessionGuard;
  private final Duration gameThreadTimeout;

  public MinecraftAdvancementProvider(
      Minecraft client,
      GameThreadExecutor clientThread,
      MinecraftSessionGuard sessionGuard,
      Duration gameThreadTimeout) {
    this.client = Objects.requireNonNull(client, "client");
    this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
    this.sessionGuard = Objects.requireNonNull(sessionGuard, "sessionGuard");
    this.gameThreadTimeout = Objects.requireNonNull(gameThreadTimeout, "gameThreadTimeout");
  }

  @Override
  public ToolResult<AdvancementSnapshot> knownAdvancements() {
    ToolResult<AdvancementReadContext> contextResult =
        MinecraftProviderSupport.read(
            clientThread, "advancements.capture_known", this::captureKnownContext);
    if (!contextResult.successful()) {
      return ToolResult.failure(Objects.requireNonNull(contextResult.error()));
    }
    AdvancementReadContext context = Objects.requireNonNull(contextResult.value());
    GameThreadExecutor serverThread =
        MinecraftThreadExecutor.forServer(context.server(), gameThreadTimeout);
    return MinecraftProviderSupport.read(
        serverThread, "advancements.snapshot", () -> readSnapshot(context));
  }

  @Override
  public ToolResult<AdvancementInfo> advancement(String advancementId) {
    Objects.requireNonNull(advancementId, "advancementId");
    ToolResult<AdvancementReadContext> contextResult =
        MinecraftProviderSupport.read(
            clientThread,
            "advancements.capture_one",
            () -> captureAdvancementContext(advancementId));
    if (!contextResult.successful()) {
      return ToolResult.failure(Objects.requireNonNull(contextResult.error()));
    }
    AdvancementReadContext context = Objects.requireNonNull(contextResult.value());
    GameThreadExecutor serverThread =
        MinecraftThreadExecutor.forServer(context.server(), gameThreadTimeout);
    return MinecraftProviderSupport.read(
        serverThread, "advancements.detail", () -> readAdvancement(context, advancementId));
  }

  private ToolResult<AdvancementReadContext> captureKnownContext() {
    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }
    ClientPacketListener connection = client.getConnection();
    MinecraftServer server = client.getSingleplayerServer();
    if (connection == null || server == null) {
      return notAvailable("The single-player advancement state is not currently available.");
    }
    List<String> knownIds =
        connection.getAdvancements().getTree().nodes().stream()
            .map(node -> node.holder().id().toString())
            .sorted()
            .limit(MAX_KNOWN_ADVANCEMENTS)
            .toList();
    int knownCount = connection.getAdvancements().getTree().nodes().size();
    return ToolResult.success(
        new AdvancementReadContext(
            server, Objects.requireNonNull(client.player).getUUID(), knownCount, knownIds));
  }

  private ToolResult<AdvancementReadContext> captureAdvancementContext(String advancementId) {
    Optional<ToolError> unavailable = sessionGuard.gameplayUnavailable(client);
    if (unavailable.isPresent()) {
      return ToolResult.failure(unavailable.orElseThrow());
    }
    ClientPacketListener connection = client.getConnection();
    MinecraftServer server = client.getSingleplayerServer();
    if (connection == null || server == null) {
      return notAvailable("The single-player advancement state is not currently available.");
    }
    Identifier id = Identifier.parse(advancementId);
    if (connection.getAdvancements().getTree().get(id) == null) {
      return ToolResult.failure(
          ToolError.of(
              ToolErrorCode.NOT_FOUND,
              "The requested advancement is not known to the player.",
              false));
    }
    return ToolResult.success(
        new AdvancementReadContext(
            server, Objects.requireNonNull(client.player).getUUID(), 1, List.of(advancementId)));
  }

  private ToolResult<AdvancementSnapshot> readSnapshot(AdvancementReadContext context) {
    ToolResult<ServerAdvancementContext> serverContextResult = serverContext(context);
    if (!serverContextResult.successful()) {
      return ToolResult.failure(Objects.requireNonNull(serverContextResult.error()));
    }
    ServerAdvancementContext serverContext = Objects.requireNonNull(serverContextResult.value());
    List<AdvancementInfo> advancements = new ArrayList<>();
    for (String advancementId : context.knownIds()) {
      AdvancementHolder holder =
          context.server().getAdvancements().get(Identifier.parse(advancementId));
      if (holder != null) {
        advancements.add(toInfo(holder, serverContext));
      }
    }
    return ToolResult.success(
        new AdvancementSnapshot(
            context.knownCount(),
            MAX_KNOWN_ADVANCEMENTS,
            context.knownCount() > advancements.size(),
            advancements));
  }

  private ToolResult<AdvancementInfo> readAdvancement(
      AdvancementReadContext context, String advancementId) {
    ToolResult<ServerAdvancementContext> serverContextResult = serverContext(context);
    if (!serverContextResult.successful()) {
      return ToolResult.failure(Objects.requireNonNull(serverContextResult.error()));
    }
    AdvancementHolder holder =
        context.server().getAdvancements().get(Identifier.parse(advancementId));
    if (holder == null) {
      return notAvailable("The requested advancement changed while it was being read.");
    }
    return ToolResult.success(toInfo(holder, Objects.requireNonNull(serverContextResult.value())));
  }

  private ToolResult<ServerAdvancementContext> serverContext(AdvancementReadContext context) {
    ServerPlayer player = context.server().getPlayerList().getPlayer(context.playerId());
    if (player == null) {
      return notAvailable("The integrated-server player is no longer available.");
    }
    return ToolResult.success(
        new ServerAdvancementContext(context.server(), player.getAdvancements()));
  }

  private static AdvancementInfo toInfo(
      AdvancementHolder holder, ServerAdvancementContext context) {
    Advancement advancement = holder.value();
    // PlayerAdvancements initializes progress entries while registering criteria listeners. The
    // client-known holder gate above means this lookup observes an existing live entry rather than
    // manufacturing visibility for an undisclosed advancement.
    AdvancementProgress progress = context.playerAdvancements().getOrStartProgress(holder);
    List<AdvancementCriterionInfo> criteria =
        advancement.criteria().keySet().stream()
            .sorted()
            .limit(AdvancementInfo.MAX_RETURNED_CRITERIA)
            .map(name -> criterion(name, progress.getCriterion(name)))
            .toList();
    int completedCriteria =
        (int)
            advancement.criteria().keySet().stream()
                .map(progress::getCriterion)
                .filter(Objects::nonNull)
                .filter(CriterionProgress::isDone)
                .count();
    AdvancementRequirements requirements = advancement.requirements();
    int completedRequirements =
        requirements.count(
            name -> {
              CriterionProgress criterion = progress.getCriterion(name);
              return criterion != null && criterion.isDone();
            });
    int totalRequirements = requirements.size();
    double completionPercentage =
        totalRequirements == 0 ? 0 : 100.0 * completedRequirements / totalRequirements;

    AdvancementNode node = context.server().getAdvancements().tree().get(holder);
    AdvancementNode root = node == null ? null : node.root();
    DisplayInfo display = advancement.display().orElse(null);
    DisplayInfo rootDisplay = root == null ? null : root.holder().value().display().orElse(null);
    Instant firstProgressAt = progress.getFirstProgressDate();
    Instant completedAt = progress.isDone() ? completionTime(requirements, progress) : null;
    return new AdvancementInfo(
        holder.id().toString(),
        display == null ? null : display.getTitle().getString(),
        display == null ? null : display.getDescription().getString(),
        progress.isDone(),
        completionPercentage,
        completedCriteria,
        advancement.criteria().size(),
        completedRequirements,
        totalRequirements,
        advancement.criteria().size() > criteria.size(),
        advancement.parent().map(Identifier::toString).orElse(null),
        root == null ? null : root.holder().id().toString(),
        rootDisplay == null ? null : rootDisplay.getTitle().getString(),
        display == null ? null : AdvancementDisplayType.valueOf(display.getType().name()),
        display == null ? null : display.isHidden(),
        instant(firstProgressAt),
        instant(completedAt),
        criteria);
  }

  private static AdvancementCriterionInfo criterion(
      String name, CriterionProgress criterionProgress) {
    Instant obtained = criterionProgress == null ? null : criterionProgress.getObtained();
    return new AdvancementCriterionInfo(name, obtained != null, instant(obtained));
  }

  private static Instant completionTime(
      AdvancementRequirements requirements, AdvancementProgress progress) {
    Instant completedAt = null;
    for (List<String> requirement : requirements.requirements()) {
      Instant requirementCompletedAt =
          requirement.stream()
              .map(progress::getCriterion)
              .filter(Objects::nonNull)
              .map(CriterionProgress::getObtained)
              .filter(Objects::nonNull)
              .min(Comparator.naturalOrder())
              .orElse(null);
      if (requirementCompletedAt == null) {
        return null;
      }
      if (completedAt == null || requirementCompletedAt.isAfter(completedAt)) {
        completedAt = requirementCompletedAt;
      }
    }
    return completedAt;
  }

  private static String instant(Instant value) {
    return value == null ? null : value.toString();
  }

  private static <T> ToolResult<T> notAvailable(String message) {
    return ToolResult.failure(ToolError.of(ToolErrorCode.NOT_AVAILABLE, message, true));
  }

  private record AdvancementReadContext(
      MinecraftServer server, UUID playerId, int knownCount, List<String> knownIds) {}

  private record ServerAdvancementContext(
      MinecraftServer server, PlayerAdvancements playerAdvancements) {}
}
