package me.clutchy.thread.core.integration;

/** Resolves an integration implementation only after discovery has accepted its candidate. */
@FunctionalInterface
public interface IntegrationLoader {
  /**
   * Loads one public no-argument integration implementation.
   *
   * @throws IntegrationLoadException when the class is missing, invalid, or cannot be constructed
   */
  ThreadIntegration load(String implementationClassName) throws IntegrationLoadException;
}
