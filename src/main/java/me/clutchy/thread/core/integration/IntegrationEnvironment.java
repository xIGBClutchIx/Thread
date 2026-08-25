package me.clutchy.thread.core.integration;

import java.util.Optional;

/** Loader-neutral view of installed mods used before any optional integration class is resolved. */
public interface IntegrationEnvironment {
  /** Returns the installed target-mod version without loading any classes from that mod. */
  Optional<String> loadedModVersion(String modId);

  /** Returns whether the installed target-mod version satisfies the declared requirement. */
  boolean versionCompatible(String modId, String versionRequirement);
}
