package me.clutchy.thread.platform.fabric.integration.jei;

import java.util.Objects;
import me.clutchy.thread.core.integration.IntegrationContext;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegration;
import me.clutchy.thread.platform.fabric.integration.FabricIntegrationServices;

/** Optional JEI recipe adapter activated only after compatible JEI discovery succeeds. */
public final class JeiIntegration implements ThreadIntegration {
  private static final IntegrationId ID = IntegrationId.of("jei");
  private final FabricIntegrationServices services;

  /** Receives Fabric services from the post-discovery platform loader. */
  public JeiIntegration(FabricIntegrationServices services) {
    this.services = Objects.requireNonNull(services, "services");
  }

  @Override
  public IntegrationId id() {
    return ID;
  }

  @Override
  public String version() {
    return "1.0.0";
  }

  @Override
  public String description() {
    return "Reads supported item recipes from JEI's live recipe catalog.";
  }

  @Override
  public void register(IntegrationContext context) {
    context.registerRecipeProvider(new JeiRecipeProvider(services));
    context.putMetadata("jei.recipe_model", "item-inputs-and-single-item-output");
    context.putMetadata("jei.unsupported_policy", "skip");
  }
}
