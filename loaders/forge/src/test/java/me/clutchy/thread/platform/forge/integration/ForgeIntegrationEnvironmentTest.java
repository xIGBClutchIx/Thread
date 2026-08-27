package me.clutchy.thread.platform.forge.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

class ForgeIntegrationEnvironmentTest {
  private final ArtifactVersion version = new DefaultArtifactVersion("2.4.1");

  @Test
  void evaluatesForgeRanges() {
    assertTrue(ForgeIntegrationEnvironment.versionCompatible(version, "[2.0,3.0)"));
    assertFalse(ForgeIntegrationEnvironment.versionCompatible(version, "[3.0,)"));
  }

  @Test
  void rejectsMalformedCandidateRanges() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ForgeIntegrationEnvironment.versionCompatible(version, "[1.0,2.0"));
  }
}
