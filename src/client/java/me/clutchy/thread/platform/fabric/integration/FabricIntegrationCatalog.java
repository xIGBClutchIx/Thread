package me.clutchy.thread.platform.fabric.integration;

import java.util.List;
import me.clutchy.thread.core.integration.IntegrationCandidate;

/**
 * Metadata-only catalog of optional Fabric integrations shipped by Thread.
 *
 * <p>Candidate entries must use implementation class-name strings. A direct class reference here
 * would defeat the absent-mod classloading guarantee. This slice intentionally ships no substantial
 * third-party integration; fake candidates prove the framework in tests.
 */
public final class FabricIntegrationCatalog {
  private FabricIntegrationCatalog() {}

  /** Returns optional candidates in declaration order; discovery applies stable ID ordering. */
  public static List<IntegrationCandidate> candidates() {
    return List.of();
  }
}
