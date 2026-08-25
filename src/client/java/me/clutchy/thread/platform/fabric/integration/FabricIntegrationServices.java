package me.clutchy.thread.platform.fabric.integration;

import java.util.Objects;
import me.clutchy.thread.core.provider.GameThreadExecutor;
import me.clutchy.thread.platform.fabric.game.FabricProviderLimits;
import me.clutchy.thread.platform.fabric.mapping.FabricDtoMapper;

/** Fabric-owned services available to optional integration implementations after discovery. */
public record FabricIntegrationServices(
    GameThreadExecutor clientThread, FabricProviderLimits limits, FabricDtoMapper mapper) {
  public FabricIntegrationServices {
    Objects.requireNonNull(clientThread, "clientThread");
    Objects.requireNonNull(limits, "limits");
    Objects.requireNonNull(mapper, "mapper");
  }
}
