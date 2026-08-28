package me.clutchy.thread.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import me.clutchy.thread.core.error.DuplicateRegistrationException;
import me.clutchy.thread.core.integration.extension.CoreIntegrationExtensionPoints;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionPoint;
import me.clutchy.thread.core.integration.extension.IntegrationExtensionRegistry;
import me.clutchy.thread.core.integration.testing.ProofIntegration;
import me.clutchy.thread.core.testing.TestJsonContracts;
import me.clutchy.thread.core.tool.ToolId;
import me.clutchy.thread.core.tool.ToolRegistry;
import me.clutchy.thread.core.tool.ToolResult;
import org.junit.jupiter.api.Test;

class IntegrationRegistryTest {
  private static final IntegrationExtensionPoint<String> ORDERED_EXTENSION =
      new IntegrationExtensionPoint<>("test.ordered", String.class);

  @Test
  void registersRequiredIntegrationsAndListsThemInStableOrder() {
    Harness harness = harness();
    AtomicInteger registrations = new AtomicInteger();
    harness.registry().register(integration("zulu", registrations, ignored -> {}));
    harness.registry().register(integration("alpha", registrations, ignored -> {}));

    assertEquals(2, registrations.get());
    assertEquals(
        List.of(IntegrationId.of("alpha"), IntegrationId.of("zulu")),
        harness.registry().integrations().stream().map(IntegrationInfo::id).toList());
  }

  @Test
  void duplicateRequiredIdIsRejectedBeforeItsCallbackRuns() {
    Harness harness = harness();
    AtomicInteger registrations = new AtomicInteger();
    harness.registry().register(integration("vanilla", registrations, ignored -> {}));

    DuplicateRegistrationException exception =
        assertThrows(
            DuplicateRegistrationException.class,
            () ->
                harness.registry().register(integration("vanilla", registrations, ignored -> {})));

    assertEquals("duplicate integration ID: vanilla", exception.getMessage());
    assertEquals(1, registrations.get());
  }

  @Test
  void failedRequiredRegistrationCommitsNothing() {
    Harness harness = harness();

    assertThrows(
        IllegalStateException.class,
        () ->
            harness
                .registry()
                .register(
                    integration(
                        "failed",
                        new AtomicInteger(),
                        context -> {
                          context.registerTool(TestJsonContracts.echoTool("failed.echo"));
                          context.registerRecipeProvider(itemId -> ToolResult.success(List.of()));
                          context.contribute(ORDERED_EXTENSION, "failed");
                          context.putMetadata("failed.mode", "test");
                          throw new IllegalStateException("failed");
                        })));

    assertTrue(harness.registry().integrations().isEmpty());
    assertFalse(harness.tools().contains(ToolId.of("failed.echo")));
    assertTrue(
        harness
            .extensions()
            .contributions(CoreIntegrationExtensionPoints.RECIPE_PROVIDER)
            .isEmpty());
    assertTrue(harness.extensions().contributions(ORDERED_EXTENSION).isEmpty());
  }

  @Test
  void presentCompatibleProofIntegrationContributesAndReportsMetadata() {
    Harness harness = harness();
    IntegrationCandidate candidate =
        candidate("proof", "proof-mod", ProofIntegration.class.getName());

    List<IntegrationActivation> activations =
        harness
            .registry()
            .discover(
                List.of(candidate),
                environment(Map.of("proof-mod", "2.4.0"), Set.of()),
                ignored -> true,
                new ReflectiveIntegrationLoader(ProofIntegration.class.getClassLoader()));

    assertEquals(IntegrationActivationStatus.ACTIVE, activations.getFirst().status());
    assertTrue(harness.tools().contains(ToolId.of("proof.echo")));
    assertEquals(
        1,
        harness.extensions().contributions(CoreIntegrationExtensionPoints.RECIPE_PROVIDER).size());
    assertEquals(1, harness.extensions().contributions(ProofIntegration.PROOF_EXTENSION).size());

    IntegrationInfo info = harness.registry().integrations().getFirst();
    assertEquals("proof", info.id().value());
    assertEquals("1.2.3", info.version());
    assertEquals("test", info.metadata().get("proof.mode"));
    assertEquals("proof-mod", info.metadata().get("thread.target_mod"));
    assertEquals("2.4.0", info.metadata().get("thread.target_mod_version"));
    assertEquals("1", info.metadata().get("thread.tool_count"));
    assertEquals(
        "proof.callback,thread.recipe_provider", info.metadata().get("thread.extension_points"));
  }

  @Test
  void absentIncompatibleAndDisabledCandidatesNeverReachTheLoader() {
    Harness harness = harness();
    AtomicInteger loads = new AtomicInteger();
    IntegrationLoader loader =
        className -> {
          loads.incrementAndGet();
          throw new AssertionError("loader must not run");
        };

    List<IntegrationActivation> activations =
        harness
            .registry()
            .discover(
                List.of(
                    candidate("incompatible", "incompatible-mod", "test.Incompatible"),
                    candidate("absent", "absent-mod", "missing.OptionalIntegration"),
                    candidate("disabled", "disabled-mod", "test.Disabled")),
                environment(
                    Map.of("incompatible-mod", "1.0", "disabled-mod", "1.0"),
                    Set.of("incompatible-mod")),
                id -> !id.equals(IntegrationId.of("disabled")),
                loader);

    assertEquals(
        List.of(
            IntegrationActivationStatus.UNAVAILABLE,
            IntegrationActivationStatus.DISABLED,
            IntegrationActivationStatus.INCOMPATIBLE),
        activations.stream().map(IntegrationActivation::status).toList());
    assertEquals(0, loads.get());
    assertTrue(harness.registry().integrations().isEmpty());
  }

  @Test
  void absentCandidateDoesNotResolveItsMissingImplementationClass() {
    Harness harness = harness();

    IntegrationActivation activation =
        harness
            .registry()
            .discover(
                List.of(candidate("absent", "absent-mod", "missing.optional.Integration")),
                environment(Map.of(), Set.of()),
                ignored -> true,
                new ReflectiveIntegrationLoader(getClass().getClassLoader()))
            .getFirst();

    assertEquals(IntegrationActivationStatus.UNAVAILABLE, activation.status());
  }

  @Test
  void duplicateCandidateIdsAreRejectedBeforeClassLoading() {
    Harness harness = harness();
    AtomicInteger loads = new AtomicInteger();

    List<IntegrationActivation> activations =
        harness
            .registry()
            .discover(
                List.of(
                    candidate("duplicate", "one-mod", "test.One"),
                    candidate("duplicate", "two-mod", "test.Two")),
                environment(Map.of("one-mod", "1", "two-mod", "1"), Set.of()),
                ignored -> true,
                className -> {
                  loads.incrementAndGet();
                  return integration("duplicate", new AtomicInteger(), ignored -> {});
                });

    assertTrue(
        activations.stream()
            .allMatch(value -> value.status() == IntegrationActivationStatus.FAILED));
    assertEquals(0, loads.get());
  }

  @Test
  void discoveryContinuesAfterLinkageAndRegistrationFailures() {
    Harness harness = harness();
    Map<String, String> versions =
        Map.of("linkage-mod", "1", "registration-mod", "1", "healthy-mod", "1");

    List<IntegrationActivation> activations =
        harness
            .registry()
            .discover(
                List.of(
                    candidate("linkage", "linkage-mod", "test.Linkage"),
                    candidate("registration", "registration-mod", "test.Registration"),
                    candidate("healthy", "healthy-mod", "test.Healthy")),
                environment(versions, Set.of()),
                ignored -> true,
                className ->
                    switch (className) {
                      case "test.Linkage" -> throw new NoClassDefFoundError("optional dependency");
                      case "test.Registration" ->
                          integration(
                              "registration",
                              new AtomicInteger(),
                              context -> {
                                context.registerTool(
                                    TestJsonContracts.echoTool("registration.partial"));
                                throw new IllegalStateException("registration failed");
                              });
                      case "test.Healthy" ->
                          integration(
                              "healthy",
                              new AtomicInteger(),
                              context ->
                                  context.registerTool(TestJsonContracts.echoTool("healthy.echo")));
                      default -> throw new AssertionError(className);
                    });

    assertEquals(
        List.of(
            IntegrationActivationStatus.ACTIVE,
            IntegrationActivationStatus.FAILED,
            IntegrationActivationStatus.FAILED),
        activations.stream().map(IntegrationActivation::status).toList());
    assertEquals(
        List.of(IntegrationId.of("healthy")),
        harness.registry().integrations().stream().map(IntegrationInfo::id).toList());
    assertTrue(harness.tools().contains(ToolId.of("healthy.echo")));
    assertFalse(harness.tools().contains(ToolId.of("registration.partial")));
  }

  @Test
  void discoveryAndContributionsUseStableIntegrationIdOrder() {
    Harness harness = harness();
    List<IntegrationActivation> activations =
        harness
            .registry()
            .discover(
                List.of(
                    candidate("zulu", "zulu-mod", "test.Zulu"),
                    candidate("alpha", "alpha-mod", "test.Alpha")),
                environment(Map.of("zulu-mod", "1", "alpha-mod", "1"), Set.of()),
                ignored -> true,
                className -> {
                  String id = className.endsWith("Alpha") ? "alpha" : "zulu";
                  return integration(
                      id,
                      new AtomicInteger(),
                      context -> {
                        context.contribute(ORDERED_EXTENSION, id + "-first");
                        context.contribute(ORDERED_EXTENSION, id + "-second");
                      });
                });

    assertEquals(
        List.of(IntegrationId.of("alpha"), IntegrationId.of("zulu")),
        activations.stream().map(IntegrationActivation::id).toList());
    assertEquals(
        List.of("alpha-first", "alpha-second", "zulu-first", "zulu-second"),
        harness.extensions().contributions(ORDERED_EXTENSION));
  }

  private static Harness harness() {
    ToolRegistry tools = new ToolRegistry();
    IntegrationExtensionRegistry extensions = new IntegrationExtensionRegistry();
    return new Harness(tools, extensions, new IntegrationRegistry(tools, extensions));
  }

  private static IntegrationCandidate candidate(String id, String modId, String className) {
    return new IntegrationCandidate(IntegrationId.of(id), modId, ">=1", className);
  }

  private static IntegrationEnvironment environment(
      Map<String, String> versions, Set<String> incompatibleMods) {
    return new IntegrationEnvironment() {
      @Override
      public Optional<String> loadedModVersion(String modId) {
        return Optional.ofNullable(versions.get(modId));
      }

      @Override
      public boolean versionCompatible(String modId, String versionRequirement) {
        return versions.containsKey(modId) && !incompatibleMods.contains(modId);
      }
    };
  }

  private static ThreadIntegration integration(
      String id, AtomicInteger registrations, Consumer<IntegrationContext> contribution) {
    return new ThreadIntegration() {
      @Override
      public IntegrationId id() {
        return IntegrationId.of(id);
      }

      @Override
      public String version() {
        return "1.0.0";
      }

      @Override
      public String description() {
        return "Test integration.";
      }

      @Override
      public void register(IntegrationContext context) {
        registrations.incrementAndGet();
        contribution.accept(context);
      }
    };
  }

  private record Harness(
      ToolRegistry tools, IntegrationExtensionRegistry extensions, IntegrationRegistry registry) {}
}
