package me.clutchy.thread.core.model.options;

import java.util.List;
import java.util.Objects;

/** Detached, sectioned snapshot of local Minecraft client settings. */
public record ClientOptionsSnapshot(
    List<ClientOptionsSection> sections,
    General general,
    Video video,
    Audio audio,
    Controls controls,
    Accessibility accessibility,
    Chat chat,
    Keybinds keybinds) {
  public ClientOptionsSnapshot {
    sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    if (sections.isEmpty()) {
      throw new IllegalArgumentException("sections must not be empty");
    }
    requireSection(sections, ClientOptionsSection.GENERAL, general);
    requireSection(sections, ClientOptionsSection.VIDEO, video);
    requireSection(sections, ClientOptionsSection.AUDIO, audio);
    requireSection(sections, ClientOptionsSection.CONTROLS, controls);
    requireSection(sections, ClientOptionsSection.ACCESSIBILITY, accessibility);
    requireSection(sections, ClientOptionsSection.CHAT, chat);
    requireSection(sections, ClientOptionsSection.KEYBINDS, keybinds);
  }

  /** Compact options that do not belong to a more focused section. */
  public record General(
      String languageCode,
      String mainHand,
      boolean pauseOnLostFocus,
      boolean advancedItemTooltips) {
    public General {
      Objects.requireNonNull(languageCode, "languageCode");
      Objects.requireNonNull(mainHand, "mainHand");
    }
  }

  /** Selected video and presentation options. */
  public record Video(
      boolean fullscreen,
      String graphicsMode,
      int renderDistance,
      int simulationDistance,
      boolean vsync,
      int fpsLimit,
      int guiScale,
      double gamma,
      String particles,
      int mipmapLevel,
      boolean entityShadows,
      int fov) {
    public Video {
      Objects.requireNonNull(graphicsMode, "graphicsMode");
      Objects.requireNonNull(particles, "particles");
    }
  }

  /** Master, per-category, device, and spatial audio options. */
  public record Audio(
      double masterVolume,
      List<SoundCategoryVolume> categoryVolumes,
      String outputDevice,
      boolean directionalAudio) {
    public Audio {
      categoryVolumes = List.copyOf(Objects.requireNonNull(categoryVolumes, "categoryVolumes"));
    }
  }

  /** Volume for one stable Minecraft sound category name. */
  public record SoundCategoryVolume(String category, double volume) {
    public SoundCategoryVolume {
      Objects.requireNonNull(category, "category");
    }
  }

  /** Mouse, movement-assist, crouch, and sprint behavior. */
  public record Controls(
      double mouseSensitivity,
      boolean invertMouseX,
      boolean invertMouseY,
      boolean rawInput,
      boolean autoJump,
      ToggleMode crouchMode,
      ToggleMode sprintMode) {
    public Controls {
      Objects.requireNonNull(crouchMode, "crouchMode");
      Objects.requireNonNull(sprintMode, "sprintMode");
    }
  }

  /** Accessibility options that have stable native values. */
  public record Accessibility(
      boolean subtitles,
      String narrator,
      boolean narratorHotkey,
      boolean highContrast,
      boolean highContrastBlockOutline,
      boolean forceUnicodeFont,
      boolean hideLightningFlashes,
      double notificationDisplayTime) {
    public Accessibility {
      Objects.requireNonNull(narrator, "narrator");
    }
  }

  /** Chat visibility and presentation options. */
  public record Chat(
      String visibility,
      double opacity,
      double scale,
      double lineSpacing,
      double textBackgroundOpacity,
      boolean backgroundForChatOnly,
      boolean colors,
      boolean links,
      boolean linksPrompt,
      boolean onlyShowSecureChat) {
    public Chat {
      Objects.requireNonNull(visibility, "visibility");
    }
  }

  /** Bounded keybind page and response-size metadata. */
  public record Keybinds(
      int totalCount, int returnedCount, int limit, boolean truncated, List<Keybind> bindings) {
    public Keybinds {
      bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
      if (returnedCount != bindings.size()) {
        throw new IllegalArgumentException("returnedCount must match bindings size");
      }
    }
  }

  /** One stable keybind mapping with bounded conflict IDs. */
  public record Keybind(
      String actionId,
      String displayName,
      String categoryId,
      String categoryDisplayName,
      InputType inputType,
      String boundInput,
      String boundDisplayName,
      boolean unbound,
      boolean defaultBinding,
      List<String> conflicts,
      boolean conflictsTruncated) {
    public static final int MAX_CONFLICTS = 16;

    public Keybind {
      Objects.requireNonNull(actionId, "actionId");
      Objects.requireNonNull(displayName, "displayName");
      Objects.requireNonNull(categoryId, "categoryId");
      Objects.requireNonNull(categoryDisplayName, "categoryDisplayName");
      Objects.requireNonNull(inputType, "inputType");
      conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
      if (conflicts.size() > MAX_CONFLICTS) {
        throw new IllegalArgumentException("too many keybind conflicts");
      }
      if (unbound != (inputType == InputType.UNBOUND)) {
        throw new IllegalArgumentException("unbound and inputType must agree");
      }
      if (unbound && (boundInput != null || boundDisplayName != null)) {
        throw new IllegalArgumentException("unbound keys must omit bound input details");
      }
      if (!unbound && (boundInput == null || boundDisplayName == null)) {
        throw new IllegalArgumentException("bound keys must include bound input details");
      }
    }
  }

  /** Stable input family independent of Minecraft's native key representation. */
  public enum InputType {
    KEYBOARD,
    MOUSE,
    SCANCODE,
    UNBOUND
  }

  /** Whether crouch or sprint activates while held or toggles across presses. */
  public enum ToggleMode {
    HOLD,
    TOGGLE
  }

  private static void requireSection(
      List<ClientOptionsSection> sections, ClientOptionsSection section, Object value) {
    if (sections.contains(section) != (value != null)) {
      throw new IllegalArgumentException(section + " section presence does not match its value");
    }
  }
}
