package me.clutchy.thread.platform.fabric.integration;

import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.core.integration.IntegrationEnvironment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/** Fabric Loader metadata view used before optional integration classes are resolved. */
public final class FabricIntegrationEnvironment implements IntegrationEnvironment {
  private final FabricLoader loader;

  public FabricIntegrationEnvironment(FabricLoader loader) {
    this.loader = Objects.requireNonNull(loader, "loader");
  }

  @Override
  public Optional<String> loadedModVersion(String modId) {
    return loader
        .getModContainer(modId)
        .map(ModContainer::getMetadata)
        .map(metadata -> metadata.getVersion().getFriendlyString());
  }

  @Override
  public boolean versionCompatible(String modId, String versionRequirement) {
    ModContainer container = loader.getModContainer(modId).orElse(null);
    if (container == null) {
      return false;
    }
    try {
      return VersionPredicate.parse(versionRequirement).test(container.getMetadata().getVersion());
    } catch (VersionParsingException exception) {
      throw new IllegalArgumentException("invalid integration version requirement", exception);
    }
  }
}
