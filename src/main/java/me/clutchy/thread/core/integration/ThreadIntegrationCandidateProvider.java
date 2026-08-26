package me.clutchy.thread.core.integration;

import java.util.List;

/**
 * Supplies metadata-only candidates from a separately distributed Thread integration package.
 *
 * <p>A platform may load this small bootstrap before it knows whether each candidate is enabled or
 * compatible. Implementations must therefore depend only on Thread's integration contracts and must
 * not import, initialize, or query their target mod APIs. The actual implementation class is named
 * by {@link IntegrationCandidate} and is resolved only after normal discovery checks pass.
 */
@FunctionalInterface
public interface ThreadIntegrationCandidateProvider {
  /**
   * Returns the integration candidates contributed by one external package.
   *
   * @return non-null candidate metadata with no null entries
   */
  List<IntegrationCandidate> candidates();
}
