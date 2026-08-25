package me.clutchy.thread.core.integration;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Loader-neutral declaration of an optional integration.
 *
 * <p>The implementation is stored as a class name rather than a class literal. This prevents JVM
 * verification from resolving optional-mod implementation classes before Thread has checked the
 * target mod and version requirement.
 *
 * @param id stable integration ID expected from the loaded implementation
 * @param targetModId mod whose presence enables the candidate
 * @param versionRequirement loader-specific version predicate evaluated by the environment
 * @param implementationClassName no-argument {@link ThreadIntegration} implementation class
 */
public record IntegrationCandidate(
    IntegrationId id,
    String targetModId,
    String versionRequirement,
    String implementationClassName) {
  private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_-]*");

  public IntegrationCandidate {
    Objects.requireNonNull(id, "id");
    targetModId = requireText(targetModId, "targetModId");
    if (!MOD_ID.matcher(targetModId).matches()) {
      throw new IllegalArgumentException("invalid target mod ID: " + targetModId);
    }
    versionRequirement = requireText(versionRequirement, "versionRequirement");
    implementationClassName = requireText(implementationClassName, "implementationClassName");
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return normalized;
  }
}
