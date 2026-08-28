package me.clutchy.thread.core.model.capability;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.clutchy.thread.core.model.validation.ModelValidation;

/** Snapshot of the actual read-only tools and game integrations active in Thread 1.0. */
public record CapabilitiesSnapshot(
    String threadVersion,
    boolean readOnly,
    List<String> tools,
    List<IntegrationCapability> integrations) {
  public CapabilitiesSnapshot {
    threadVersion = ModelValidation.nonBlank(threadVersion, "threadVersion");
    if (!readOnly) {
      throw new IllegalArgumentException("Thread 1.0 capabilities must be read-only");
    }
    tools =
        ModelValidation.immutableList(tools, "tools").stream()
            .map(tool -> ModelValidation.nonBlank(tool, "tool ID"))
            .sorted()
            .toList();
    Set<String> seenToolIds = new HashSet<>();
    for (String tool : tools) {
      if (!seenToolIds.add(tool)) {
        throw new IllegalArgumentException("capabilities contain duplicate tool ID " + tool);
      }
    }

    integrations =
        ModelValidation.immutableList(integrations, "integrations").stream()
            .sorted(Comparator.comparing(IntegrationCapability::id))
            .toList();
    Set<String> seenIntegrationIds = new HashSet<>();
    for (IntegrationCapability integration : integrations) {
      if (!seenIntegrationIds.add(integration.id())) {
        throw new IllegalArgumentException(
            "capabilities contain duplicate integration ID " + integration.id());
      }
    }
  }
}
