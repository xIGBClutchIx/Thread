package me.clutchy.thread.platform.minecraft.options;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import me.clutchy.thread.core.model.options.ClientOptionsQuery;
import me.clutchy.thread.core.model.options.ClientOptionsSection;
import me.clutchy.thread.core.model.options.ClientOptionsSnapshot;
import me.clutchy.thread.core.model.options.ClientOptionsSnapshot.InputType;
import me.clutchy.thread.core.model.options.ClientOptionsSnapshot.ToggleMode;
import me.clutchy.thread.core.provider.ClientOptionsProvider;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.core.tool.ToolResult;
import me.clutchy.thread.platform.minecraft.game.MinecraftProviderSupport;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

/** Shared loader-neutral extraction of local Minecraft client options. */
public final class MinecraftClientOptionsProvider implements ClientOptionsProvider {
  private static final Comparator<KeyMapping> KEYBIND_ORDER =
      Comparator.comparing(KeyMapping::getName).thenComparing(KeyMapping::saveString);

  private final Minecraft client;
  private final GameThreadExecutor clientThread;

  public MinecraftClientOptionsProvider(Minecraft client, GameThreadExecutor clientThread) {
    this.client = Objects.requireNonNull(client, "client");
    this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
  }

  @Override
  public ToolResult<ClientOptionsSnapshot> options(ClientOptionsQuery query) {
    Objects.requireNonNull(query, "query");
    return MinecraftProviderSupport.read(
        clientThread,
        "client_options.read",
        () -> ToolResult.success(readOptions(query, client.options)));
  }

  private static ClientOptionsSnapshot readOptions(ClientOptionsQuery query, Options options) {
    List<ClientOptionsSection> sections = query.sections();
    return new ClientOptionsSnapshot(
        sections,
        query.includes(ClientOptionsSection.GENERAL) ? general(options) : null,
        query.includes(ClientOptionsSection.VIDEO) ? video(options) : null,
        query.includes(ClientOptionsSection.AUDIO) ? audio(options) : null,
        query.includes(ClientOptionsSection.CONTROLS) ? controls(options) : null,
        query.includes(ClientOptionsSection.ACCESSIBILITY) ? accessibility(options) : null,
        query.includes(ClientOptionsSection.CHAT) ? chat(options) : null,
        query.includes(ClientOptionsSection.KEYBINDS)
            ? keybinds(options, query.keybindLimit())
            : null);
  }

  private static ClientOptionsSnapshot.General general(Options options) {
    return new ClientOptionsSnapshot.General(
        options.languageCode,
        options.mainHand().get().name(),
        options.pauseOnLostFocus,
        options.advancedItemTooltips);
  }

  private static ClientOptionsSnapshot.Video video(Options options) {
    return new ClientOptionsSnapshot.Video(
        options.fullscreen().get(),
        options.graphicsPreset().get().name(),
        options.renderDistance().get(),
        options.simulationDistance().get(),
        options.enableVsync().get(),
        options.framerateLimit().get(),
        options.guiScale().get(),
        options.gamma().get(),
        options.particles().get().name(),
        options.mipmapLevels().get(),
        options.entityShadows().get(),
        options.fov().get());
  }

  private static ClientOptionsSnapshot.Audio audio(Options options) {
    List<ClientOptionsSnapshot.SoundCategoryVolume> categories =
        Arrays.stream(SoundSource.values())
            .filter(source -> source != SoundSource.MASTER)
            .sorted(Comparator.comparing(SoundSource::getName))
            .map(
                source ->
                    new ClientOptionsSnapshot.SoundCategoryVolume(
                        source.getName(), options.getSoundSourceVolume(source)))
            .toList();
    String device = options.soundDevice().get();
    return new ClientOptionsSnapshot.Audio(
        options.getSoundSourceVolume(SoundSource.MASTER),
        categories,
        Options.isSoundDeviceDefault(device) ? null : device,
        options.directionalAudio().get());
  }

  private static ClientOptionsSnapshot.Controls controls(Options options) {
    return new ClientOptionsSnapshot.Controls(
        options.sensitivity().get(),
        options.invertMouseX().get(),
        options.invertMouseY().get(),
        options.rawMouseInput().get(),
        options.autoJump().get(),
        toggleMode(options.toggleCrouch().get()),
        toggleMode(options.toggleSprint().get()));
  }

  private static ClientOptionsSnapshot.Accessibility accessibility(Options options) {
    return new ClientOptionsSnapshot.Accessibility(
        options.showSubtitles().get(),
        options.narrator().get().name(),
        options.narratorHotkey().get(),
        options.highContrast().get(),
        options.highContrastBlockOutline().get(),
        options.forceUnicodeFont().get(),
        options.hideLightningFlash().get(),
        options.notificationDisplayTime().get());
  }

  private static ClientOptionsSnapshot.Chat chat(Options options) {
    return new ClientOptionsSnapshot.Chat(
        options.chatVisibility().get().name(),
        options.chatOpacity().get(),
        options.chatScale().get(),
        options.chatLineSpacing().get(),
        options.textBackgroundOpacity().get(),
        options.backgroundForChatOnly().get(),
        options.chatColors().get(),
        options.chatLinks().get(),
        options.chatLinksPrompt().get(),
        options.onlyShowSecureChat().get());
  }

  private static ClientOptionsSnapshot.Keybinds keybinds(Options options, int limit) {
    List<KeyMapping> all =
        Arrays.stream(options.keyMappings).filter(Objects::nonNull).sorted(KEYBIND_ORDER).toList();
    List<ClientOptionsSnapshot.Keybind> returned =
        all.stream().limit(limit).map(mapping -> keybind(mapping, all)).toList();
    return new ClientOptionsSnapshot.Keybinds(
        all.size(), returned.size(), limit, all.size() > returned.size(), returned);
  }

  private static ClientOptionsSnapshot.Keybind keybind(KeyMapping mapping, List<KeyMapping> all) {
    List<String> allConflicts =
        mapping.isUnbound()
            ? List.of()
            : all.stream()
                .filter(other -> other != mapping && !other.isUnbound() && mapping.same(other))
                .map(KeyMapping::getName)
                .distinct()
                .sorted()
                .toList();
    List<String> conflicts =
        allConflicts.stream().limit(ClientOptionsSnapshot.Keybind.MAX_CONFLICTS).toList();
    boolean unbound = mapping.isUnbound();
    String boundInput = unbound ? null : mapping.saveString();
    return new ClientOptionsSnapshot.Keybind(
        mapping.getName(),
        Component.translatable(mapping.getName()).getString(),
        mapping.getCategory().id().toString(),
        mapping.getCategory().label().getString(),
        unbound ? InputType.UNBOUND : inputType(boundInput),
        boundInput,
        unbound ? null : mapping.getTranslatedKeyMessage().getString(),
        unbound,
        mapping.isDefault(),
        conflicts,
        allConflicts.size() > conflicts.size());
  }

  private static ToggleMode toggleMode(boolean toggle) {
    return toggle ? ToggleMode.TOGGLE : ToggleMode.HOLD;
  }

  private static InputType inputType(String boundInput) {
    if (boundInput.startsWith("key.mouse.")) {
      return InputType.MOUSE;
    }
    if (boundInput.startsWith("scancode.")) {
      return InputType.SCANCODE;
    }
    return InputType.KEYBOARD;
  }
}
