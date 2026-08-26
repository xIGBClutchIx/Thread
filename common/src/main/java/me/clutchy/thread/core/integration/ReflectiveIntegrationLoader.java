package me.clutchy.thread.core.integration;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/** Class-name-based integration loader used after mod-presence and compatibility checks pass. */
public final class ReflectiveIntegrationLoader implements IntegrationLoader {
  private final ClassLoader classLoader;

  /** Creates a loader over the mod class loader supplied by the active loader entry point. */
  public ReflectiveIntegrationLoader(ClassLoader classLoader) {
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
      return ThreadIntegration.class.cast(implementation.getConstructor().newInstance());
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException exception) {
      throw new IntegrationLoadException("Could not load integration implementation", exception);
    }
  }
}
