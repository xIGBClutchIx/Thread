package me.clutchy.thread.core.integration;

/** Extension point that installs game-specific tools and context without transport coupling. */
public interface GameIntegration {
  IntegrationId id();

  String version();

  String description();

  void register(IntegrationContext context);
}
