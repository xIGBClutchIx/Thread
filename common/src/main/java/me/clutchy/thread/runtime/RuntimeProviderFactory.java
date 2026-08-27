package me.clutchy.thread.runtime;

import me.clutchy.thread.config.ThreadConfig;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;

/** Builds focused providers at the Minecraft-version boundary during runtime assembly. */
@FunctionalInterface
public interface RuntimeProviderFactory {
  /** Creates providers using validated configuration and the shared extension registry. */
  RuntimeProviders create(
      ThreadConfig config, ThreadRuntimeInfo info, IntegrationExtensionRegistry extensionRegistry);
}
