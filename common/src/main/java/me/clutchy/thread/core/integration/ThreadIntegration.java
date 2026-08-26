package me.clutchy.thread.core.integration;

/**
 * Optional, transport-independent Thread extension activated during client startup.
 *
 * <p>An integration should be a small, preferably stateless contributor. Optional mod classes may
 * implement this contract, but their implementation class must be resolved only after the
 * integration registry has confirmed that the target mod is present and compatible.
 */
public interface ThreadIntegration {
  /** Returns the globally stable integration identifier. */
  IntegrationId id();

  /** Returns the integration contract version advertised through capability discovery. */
  String version();

  /** Returns a concise client-facing summary of the integration. */
  String description();

  /**
   * Contributes only the extension points this integration supports.
   *
   * <p>Registration is transactional: contributions become visible only after this method returns
   * successfully. Implementations must not retain or mutate the context after the callback.
   */
  void register(IntegrationContext context);
}
