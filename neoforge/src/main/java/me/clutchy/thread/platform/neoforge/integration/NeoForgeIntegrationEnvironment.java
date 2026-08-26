package me.clutchy.thread.platform.neoforge.integration;

import java.util.Objects;
import java.util.Optional;
import me.clutchy.thread.core.integration.IntegrationEnvironment;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/** NeoForge metadata view used before optional integration classes are resolved. */
public final class NeoForgeIntegrationEnvironment implements IntegrationEnvironment {
  private final ModList modList;

  public NeoForgeIntegrationEnvironment(ModList modList) {
    this.modList = Objects.requireNonNull(modList, "modList");
  }

  @Override
  public Optional<String> loadedModVersion(String modId) {
    return loadedVersion(modId).map(Object::toString);
  }

  @Override
  public boolean versionCompatible(String modId, String versionRequirement) {
    ArtifactVersion version = loadedVersion(modId).orElse(null);
    if (version == null) {
      return false;
    }
    return versionCompatible(version, versionRequirement);
  }

  static boolean versionCompatible(ArtifactVersion version, String versionRequirement) {
    Objects.requireNonNull(version, "version");
    try {
      return VersionRange.createFromVersionSpec(versionRequirement).containsVersion(version);
    } catch (InvalidVersionSpecificationException exception) {
      throw new IllegalArgumentException("invalid integration version requirement", exception);
    }
  }

  private Optional<ArtifactVersion> loadedVersion(String modId) {
    return modList.getMods().stream()
        .filter(info -> info.getModId().equals(modId))
        .findFirst()
        .map(info -> info.getVersion());
  }
}
