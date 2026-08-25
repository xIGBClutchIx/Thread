package me.clutchy.thread.platform.fabric.integration;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import me.clutchy.thread.core.integration.IntegrationLoadException;
import me.clutchy.thread.core.integration.IntegrationLoader;
import me.clutchy.thread.core.integration.ThreadIntegration;

/** Constructs optional Fabric integrations only after discovery has accepted their target mod. */
public final class FabricIntegrationLoader implements IntegrationLoader {
  private final FabricIntegrationServices services;
  private final ClassLoader classLoader;

  /** Creates a loader whose integrations receive only platform-owned, transport-free services. */
  public FabricIntegrationLoader(FabricIntegrationServices services, ClassLoader classLoader) {
    this.services = Objects.requireNonNull(services, "services");
    this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
  }

  @Override
  public ThreadIntegration load(String implementationClassName) throws IntegrationLoadException {
    try {
      Class<?> implementation = Class.forName(implementationClassName, true, classLoader);
      if (!ThreadIntegration.class.isAssignableFrom(implementation)) {
        throw new IntegrationLoadException(
            "Integration implementation does not implement ThreadIntegration", null);
      }
      Object instance =
          implementation.getConstructor(FabricIntegrationServices.class).newInstance(services);
      return ThreadIntegration.class.cast(instance);
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException exception) {
      throw new IntegrationLoadException(
          "Could not load Fabric integration implementation", exception);
    }
  }
}
