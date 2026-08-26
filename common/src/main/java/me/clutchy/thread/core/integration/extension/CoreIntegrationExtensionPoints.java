package me.clutchy.thread.core.integration.extension;

/** Core-owned optional integration extension points. */
public final class CoreIntegrationExtensionPoints {
  /** Additional recipe definitions merged after the guarded vanilla provider succeeds. */
  public static final IntegrationExtensionPoint<IntegrationRecipeProvider> RECIPE_PROVIDER =
      new IntegrationExtensionPoint<>("thread.recipe_provider", IntegrationRecipeProvider.class);

  private CoreIntegrationExtensionPoints() {}
}
