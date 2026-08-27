package me.clutchy.thread.core.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import me.clutchy.thread.core.model.advancement.AdvancementFilter;
import me.clutchy.thread.core.model.advancement.AdvancementInfo;
import me.clutchy.thread.core.model.advancement.AdvancementListQuery;
import me.clutchy.thread.core.model.advancement.AdvancementListResult;
import me.clutchy.thread.core.model.advancement.AdvancementLookupQuery;
import me.clutchy.thread.core.model.advancement.AdvancementSnapshot;
import me.clutchy.thread.core.model.advancement.AdvancementSummary;
import me.clutchy.thread.core.provider.AdvancementProvider;
import me.clutchy.thread.core.tool.ToolResult;

/** Applies deterministic filtering and result bounds to vanilla advancement snapshots. */
public final class AdvancementService {
  private static final Pattern REGISTRY_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

  private final AdvancementProvider provider;

  public AdvancementService(AdvancementProvider provider) {
    this.provider = Objects.requireNonNull(provider, "provider");
  }

  /** Returns a bounded filtered view of advancements currently known to the player. */
  public ToolResult<AdvancementListResult> list(AdvancementListQuery query) {
    Objects.requireNonNull(query, "query");
    return map(provider.knownAdvancements(), snapshot -> list(query, snapshot));
  }

  /** Returns one exact known advancement without broadening the query to hidden unknown entries. */
  public ToolResult<AdvancementInfo> get(AdvancementLookupQuery query) {
    Objects.requireNonNull(query, "query");
    return provider.advancement(query.advancementId());
  }

  private static AdvancementListResult list(
      AdvancementListQuery query, AdvancementSnapshot snapshot) {
    List<AdvancementInfo> matches =
        snapshot.advancements().stream()
            .filter(info -> matchesFilter(query.filter(), info))
            .filter(info -> matchesSearch(query.search(), info))
            .toList();
    List<AdvancementSummary> returned =
        matches.stream().limit(query.limit()).map(AdvancementSummary::from).toList();
    boolean sourceTruncated = snapshot.truncated();
    boolean truncated = sourceTruncated || matches.size() > returned.size();
    return new AdvancementListResult(
        query.filter(),
        query.search(),
        query.limit(),
        snapshot.knownCount(),
        snapshot.advancements().size(),
        matches.size(),
        sourceTruncated,
        truncated,
        returned);
  }

  private static boolean matchesFilter(AdvancementFilter filter, AdvancementInfo info) {
    return switch (filter) {
      case ALL -> true;
      case COMPLETED -> info.completed();
      case INCOMPLETE -> !info.completed();
    };
  }

  private static boolean matchesSearch(String search, AdvancementInfo info) {
    if (search == null) {
      return true;
    }
    String normalized = search.strip();
    if (REGISTRY_ID.matcher(normalized).matches()) {
      return info.advancementId().equals(normalized);
    }
    String haystack =
        (info.advancementId()
                + " "
                + info.advancementId().replace('_', ' ').replace(':', ' ').replace('/', ' ')
                + " "
                + nullable(info.title())
                + " "
                + nullable(info.description())
                + " "
                + nullable(info.tabTitle()))
            .toLowerCase(Locale.ROOT);
    for (String term : normalized.toLowerCase(Locale.ROOT).split("\\s+")) {
      if (!haystack.contains(term)) {
        return false;
      }
    }
    return true;
  }

  private static String nullable(String value) {
    return value == null ? "" : value;
  }

  private static <T, R> ToolResult<R> map(
      ToolResult<T> result, Function<? super T, ? extends R> mapper) {
    if (!result.successful()) {
      return ToolResult.failure(Objects.requireNonNull(result.error()));
    }
    return ToolResult.success(mapper.apply(Objects.requireNonNull(result.value())));
  }
}
