package me.clutchy.thread.core.integration;

/** Extension point that installs game-specific tools and context without transport coupling. */
public interface GameIntegration {
  /** Returns the globally stable integration identifier. */
  IntegrationId id();

  /** Returns the integration contract version advertised through capability discovery. */
  String version();

  /** Returns a concise client-facing summary of the integration. */
  String description();

  /**
   * Installs this integration into the supplied core registries.
   *
   * <p>Registration occurs during startup. Implementations should finish all mutations before
   * returning and must not retain transport-specific state in the context.
   */
  void register(IntegrationContext context);
}
