package me.clutchy.thread.core.tool;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable per-tool support declared by one Minecraft-version adapter. */
public final class VersionCapabilities {
  private static final ToolSupport UNSUPPORTED = ToolSupport.unsupported();

  private final Map<ToolId, ToolSupport> tools;

  private VersionCapabilities(Map<ToolId, ToolSupport> tools) {
    this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(tools)));
  }

  /** Creates an explicit capability map. Tools absent from the map are unsupported. */
  public static VersionCapabilities of(Map<ToolId, ToolSupport> tools) {
    Objects.requireNonNull(tools, "tools");
    Map<ToolId, ToolSupport> copy = new LinkedHashMap<>();
    tools.forEach(
        (toolId, support) ->
            copy.put(
                Objects.requireNonNull(toolId, "tool ID"),
                Objects.requireNonNull(support, "tool support")));
    return new VersionCapabilities(copy);
  }

  /** Creates a capability map in which every supplied tool is fully supported. */
  public static VersionCapabilities fullySupported(Collection<ToolId> toolIds) {
    Objects.requireNonNull(toolIds, "toolIds");
    Map<ToolId, ToolSupport> support = new LinkedHashMap<>();
    toolIds.forEach(
        toolId ->
            support.put(Objects.requireNonNull(toolId, "tool ID"), ToolSupport.fullySupported()));
    return new VersionCapabilities(support);
  }

  /** Returns support for one tool, defaulting to unsupported when the adapter omitted it. */
  public ToolSupport support(ToolId toolId) {
    return tools.getOrDefault(Objects.requireNonNull(toolId, "toolId"), UNSUPPORTED);
  }

  /** Reports whether the adapter permits one tool to enter discovery and invocation registries. */
  public boolean advertises(ToolId toolId) {
    return support(toolId).advertised();
  }

  /** Returns the stable tool-ID-ordered support map for diagnostics and tests. */
  public Map<ToolId, ToolSupport> tools() {
    return tools;
  }
}
