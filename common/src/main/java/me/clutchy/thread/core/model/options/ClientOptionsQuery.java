package me.clutchy.thread.core.model.options;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/** Optional section selection and keybind bound for one local client-options read. */
public record ClientOptionsQuery(List<ClientOptionsSection> sections, Integer keybindLimit) {
  public static final int DEFAULT_KEYBIND_LIMIT = 64;
  public static final int MAX_KEYBIND_LIMIT = 128;

  private static final List<ClientOptionsSection> DEFAULT_SECTIONS =
      List.of(
          ClientOptionsSection.GENERAL,
          ClientOptionsSection.VIDEO,
          ClientOptionsSection.AUDIO,
          ClientOptionsSection.CONTROLS,
          ClientOptionsSection.ACCESSIBILITY,
          ClientOptionsSection.CHAT);

  public ClientOptionsQuery {
    if (sections == null) {
      sections = DEFAULT_SECTIONS;
    } else {
      if (sections.isEmpty()) {
        throw new IllegalArgumentException("sections must not be empty");
      }
      EnumSet<ClientOptionsSection> selected = EnumSet.copyOf(sections);
      sections = Arrays.stream(ClientOptionsSection.values()).filter(selected::contains).toList();
    }
    keybindLimit = keybindLimit == null ? DEFAULT_KEYBIND_LIMIT : keybindLimit;
    if (keybindLimit <= 0 || keybindLimit > MAX_KEYBIND_LIMIT) {
      throw new IllegalArgumentException("keybindLimit must be between 1 and " + MAX_KEYBIND_LIMIT);
    }
  }

  /** Reports whether the normalized query requests one section. */
  public boolean includes(ClientOptionsSection section) {
    return sections.contains(section);
  }

  /** Returns the immutable section selection used when a request omits {@code sections}. */
  public static List<ClientOptionsSection> defaultSections() {
    return DEFAULT_SECTIONS;
  }
}
