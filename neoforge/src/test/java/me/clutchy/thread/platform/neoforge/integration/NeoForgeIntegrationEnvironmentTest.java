package me.clutchy.thread.platform.neoforge.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

class NeoForgeIntegrationEnvironmentTest {
  private final Map<String, ArtifactVersion> versions =
      Map.of("example", new DefaultArtifactVersion("2.4.1"));
  private final NeoForgeIntegrationEnvironment environment =
      new NeoForgeIntegrationEnvironment(modId -> Optional.ofNullable(versions.get(modId)));

  @Test
  void exposesLoadedVersionsAndEvaluatesNeoForgeRanges() {
    assertEquals(Optional.of("2.4.1"), environment.loadedModVersion("example"));
    assertEquals(Optional.empty(), environment.loadedModVersion("missing"));
    assertTrue(environment.versionCompatible("example", "[2.0,3.0)"));
    assertFalse(environment.versionCompatible("example", "[3.0,)"));
    assertFalse(environment.versionCompatible("missing", "[1.0,)"));
  }

  @Test
  void rejectsMalformedCandidateRanges() {
    assertThrows(
        IllegalArgumentException.class, () -> environment.versionCompatible("example", "[1.0,2.0"));
  }
}
