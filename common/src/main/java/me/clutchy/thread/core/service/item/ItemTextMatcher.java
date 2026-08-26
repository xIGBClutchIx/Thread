package me.clutchy.thread.core.service.item;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared exact-ID and friendly-text matching used by registry and live-source item searches. */
public final class ItemTextMatcher {
  private static final Pattern REGISTRY_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

  private ItemTextMatcher() {}

  /** Returns whether the item matches one canonical registry ID or all friendly text terms. */
  public static boolean matches(
      String query, String itemId, String displayName, String customName) {
    String normalizedQuery = query.strip();
    if (REGISTRY_ID.matcher(normalizedQuery).matches()) {
      return itemId.equals(normalizedQuery);
    }
    String haystack =
        (itemId
                + " "
                + itemId.replace('_', ' ').replace(':', ' ')
                + " "
                + displayName
                + " "
                + (customName == null ? "" : customName))
            .toLowerCase(Locale.ROOT);
    for (String term : normalizedQuery.toLowerCase(Locale.ROOT).split("\\s+")) {
      if (!haystack.contains(term)) {
        return false;
      }
    }
    return true;
  }
}
