package me.clutchy.thread.core.model.item.find;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import me.clutchy.thread.core.model.item.ItemInfo;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** One matching item identity with a total count and deterministic contributing sources. */
public record FoundItem(ItemInfo item, int totalCount, List<FoundItemSource> sources) {
  private static final Comparator<FoundItemSource> SOURCE_ORDER =
      Comparator.comparing(FoundItemSource::sourceType)
          .thenComparing(source -> source.distance() == null ? -1D : source.distance())
          .thenComparing(
              source -> source.containerPosition() == null ? 0 : source.containerPosition().x())
          .thenComparing(
              source -> source.containerPosition() == null ? 0 : source.containerPosition().y())
          .thenComparing(
              source -> source.containerPosition() == null ? 0 : source.containerPosition().z())
          .thenComparing(
              source -> source.containerTypeId() == null ? "" : source.containerTypeId());

  public FoundItem {
    Objects.requireNonNull(item, "item");
    if (totalCount <= 0) {
      throw new IllegalArgumentException("totalCount must be positive");
    }
    sources =
        ModelValidation.immutableList(sources, "sources").stream().sorted(SOURCE_ORDER).toList();
    if (sources.isEmpty()) {
      throw new IllegalArgumentException("sources must not be empty");
    }
    int calculatedTotal = 0;
    Set<String> sourceKeys = new HashSet<>();
    for (FoundItemSource source : sources) {
      calculatedTotal = Math.addExact(calculatedTotal, source.count());
      String sourceKey =
          source.sourceType()
              + ":"
              + Objects.toString(source.containerPosition(), "")
              + ":"
              + Objects.toString(source.containerTypeId(), "");
      if (!sourceKeys.add(sourceKey)) {
        throw new IllegalArgumentException("sources contains a duplicate location");
      }
    }
    if (calculatedTotal != totalCount) {
      throw new IllegalArgumentException("totalCount must equal the sum of source counts");
    }
  }
}
