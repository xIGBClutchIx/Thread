package me.clutchy.thread.platform.forge.integration;

import java.util.Optional;
import me.clutchy.thread.core.integration.IntegrationEnvironment;
import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/** Forge metadata view used before optional integration classes are resolved. */
public final class ForgeIntegrationEnvironment implements IntegrationEnvironment {
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
    java.util.Objects.requireNonNull(version, "version");
    try {
      return VersionRange.createFromVersionSpec(versionRequirement).containsVersion(version);
    } catch (InvalidVersionSpecificationException exception) {
      throw new IllegalArgumentException("invalid integration version requirement", exception);
    }
  }

  private Optional<ArtifactVersion> loadedVersion(String modId) {
    return ModList.getMods().stream()
        .filter(info -> info.getModId().equals(modId))
        .findFirst()
        .map(info -> info.getVersion());
  }
}
