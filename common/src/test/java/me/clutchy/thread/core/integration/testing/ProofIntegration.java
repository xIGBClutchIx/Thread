package me.clutchy.thread.core.integration.testing;

import java.util.List;
import me.clutchy.thread.core.integration.IntegrationContext;
import me.clutchy.thread.core.integration.IntegrationId;
import me.clutchy.thread.core.integration.ThreadIntegration;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionPoint;
import me.clutchy.thread.core.testing.TestJsonContracts;
import me.clutchy.thread.core.tool.ToolResult;

/** Test-only proof integration exercising each core contribution path. */
public final class ProofIntegration implements ThreadIntegration {
  public static final IntegrationExtensionPoint<Runnable> PROOF_EXTENSION =
      new IntegrationExtensionPoint<>("proof.callback", Runnable.class);

  public ProofIntegration() {}

  @Override
  public IntegrationId id() {
    return IntegrationId.of("proof");
  }

  @Override
  public String version() {
    return "1.2.3";
  }

  @Override
  public String description() {
    return "Test-only optional integration.";
  }

  @Override
  public void register(IntegrationContext context) {
    context.registerTool(TestJsonContracts.echoTool("proof.echo"));
    context.registerRecipeProvider(itemId -> ToolResult.success(List.of()));
    context.contribute(PROOF_EXTENSION, () -> {});
    context.putMetadata("proof.mode", "test");
  }
}
