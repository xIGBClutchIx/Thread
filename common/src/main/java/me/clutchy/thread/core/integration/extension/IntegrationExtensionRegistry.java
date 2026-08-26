package me.clutchy.thread.core.integration.extension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import me.clutchy.thread.core.integration.IntegrationId;

/**
 * Deterministic typed registry for optional contributions whose contracts live outside core.
 *
 * <p>Core knows only the extension-point ID and contract class. Minecraft-facing points may
 * therefore accept Minecraft-facing adapters without importing those types into core packages.
 */
public final class IntegrationExtensionRegistry {
  private final Map<String, PointState> points = new HashMap<>();
  private long nextOrdinal;

  /** Validates that the point and contribution can be accepted without mutating the registry. */
  public synchronized <T> void validate(IntegrationExtensionPoint<T> point, T contribution) {
    Objects.requireNonNull(point, "point");
    Objects.requireNonNull(contribution, "contribution");
    if (!point.contract().isInstance(contribution)) {
      throw new IllegalArgumentException(
          "contribution does not implement " + point.contract().getName());
    }
    PointState existing = points.get(point.id());
    if (existing != null && !existing.contract().equals(point.contract())) {
      throw new IllegalArgumentException(
          "extension point ID is already bound to " + existing.contract().getName());
    }
  }

  /** Registers one contribution under its owning integration. */
  public synchronized <T> void register(
      IntegrationId owner, IntegrationExtensionPoint<T> point, T contribution) {
    Objects.requireNonNull(owner, "owner");
    validate(point, contribution);
    PointState state =
        points.computeIfAbsent(point.id(), ignored -> new PointState(point.contract()));
    state.contributions().add(new RegisteredContribution(owner, nextOrdinal++, contribution));
  }

  /**
   * Returns contributions sorted by stable integration ID and their registration order within that
   * integration.
   */
  public synchronized <T> List<T> contributions(IntegrationExtensionPoint<T> point) {
    Objects.requireNonNull(point, "point");
    PointState state = points.get(point.id());
    if (state == null) {
      return List.of();
    }
    if (!state.contract().equals(point.contract())) {
      throw new IllegalArgumentException(
          "extension point ID is bound to a different contract: " + point.id());
    }
    return state.contributions().stream()
        .sorted(
            Comparator.comparing(RegisteredContribution::owner)
                .thenComparingLong(RegisteredContribution::ordinal))
        .map(RegisteredContribution::contribution)
        .map(point.contract()::cast)
        .toList();
  }

  private record PointState(Class<?> contract, List<RegisteredContribution> contributions) {
    private PointState(Class<?> contract) {
      this(contract, new ArrayList<>());
    }
  }

  private record RegisteredContribution(IntegrationId owner, long ordinal, Object contribution) {}
}
