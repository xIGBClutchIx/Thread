package dev.xigbclutch.thread.core.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.xigbclutch.thread.core.context.ContextRegistry;
import dev.xigbclutch.thread.core.error.DuplicateRegistrationException;
import dev.xigbclutch.thread.core.tool.ToolRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IntegrationRegistryTest {
  @Test
  void activatesIntegrationsAndListsThemInStableOrder() {
    IntegrationRegistry registry = registry();
    AtomicInteger registrations = new AtomicInteger();
    registry.register(new TestIntegration("zulu", registrations));
    registry.register(new TestIntegration("alpha", registrations));

    assertEquals(2, registrations.get());
    assertEquals(
        List.of(IntegrationId.of("alpha"), IntegrationId.of("zulu")),
        registry.integrations().stream().map(IntegrationInfo::id).toList());
  }

  @Test
  void duplicateIsRejectedBeforeItsRegistrationCallbackRuns() {
    IntegrationRegistry registry = registry();
    AtomicInteger registrations = new AtomicInteger();
    registry.register(new TestIntegration("vanilla", registrations));

    DuplicateRegistrationException exception =
        assertThrows(
            DuplicateRegistrationException.class,
            () -> registry.register(new TestIntegration("vanilla", registrations)));

    assertEquals("duplicate integration ID: vanilla", exception.getMessage());
    assertEquals(1, registrations.get());
  }

  @Test
  void failedRegistrationIsNotReportedAsActive() {
    IntegrationRegistry registry = registry();

    assertThrows(IllegalStateException.class, () -> registry.register(new FailedIntegration()));

    assertFalse(
        registry.integrations().stream()
            .anyMatch(info -> info.id().equals(IntegrationId.of("failed"))));
  }

  private static IntegrationRegistry registry() {
    return new IntegrationRegistry(
        new IntegrationContext(new ToolRegistry(), new ContextRegistry()));
  }

  private record TestIntegration(IntegrationId id, AtomicInteger registrations)
      implements GameIntegration {
    private TestIntegration(String id, AtomicInteger registrations) {
      this(IntegrationId.of(id), registrations);
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
    }
  }

  private static final class FailedIntegration implements GameIntegration {
    @Override
    public IntegrationId id() {
      return IntegrationId.of("failed");
    }

    @Override
    public String version() {
      return "1.0.0";
    }

    @Override
    public String description() {
      return "Fails during registration.";
    }

    @Override
    public void register(IntegrationContext context) {
      throw new IllegalStateException("failed");
    }
  }
}
