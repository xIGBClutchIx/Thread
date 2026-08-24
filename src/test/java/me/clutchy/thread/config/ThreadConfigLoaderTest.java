package me.clutchy.thread.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThreadConfigLoaderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void createsAndReloadsAStableDefaultFile() throws IOException {
    Path path = temporaryDirectory.resolve("nested/thread.json");

    ThreadConfig created = ThreadConfigLoader.loadOrCreate(path);
    ThreadConfig loaded = ThreadConfigLoader.loadOrCreate(path);

    assertEquals(ThreadConfig.defaults(), created);
    assertEquals(created, loaded);
    String source = Files.readString(path);
    assertTrue(source.contains("\"schemaVersion\": 1"));
    assertTrue(source.endsWith(System.lineSeparator()));
  }

  @Test
  void appliesOverridesWhileRetainingOmittedDefaults() throws IOException {
    Path path = temporaryDirectory.resolve("thread.json");
    Files.writeString(
        path,
        """
        {
          "schemaVersion": 1,
          "mcpEnabled": false,
          "mcpBindHost": "localhost",
          "mcpPort": 24444,
          "enabledTools": ["minecraft.get_status"],
          "maxEntityRadius": 24.0,
          "maxEntityResults": 12,
          "maxItemSearchResults": 10,
          "maxRequestBytes": 4096,
          "gameThreadTimeoutMillis": 250,
          "maxConcurrentRequests": 2
        }
        """);

    ThreadConfig config = ThreadConfigLoader.loadOrCreate(path);

    assertFalse(config.mcpEnabled());
    assertEquals("localhost", config.mcpBindHost());
    assertEquals(24_444, config.mcpPort());
    assertEquals(24.0, config.maxEntityRadius());
    assertEquals(12, config.maxEntityResults());
    assertEquals(10, config.maxItemSearchResults());
    assertEquals(4096, config.maxRequestBytes());
    assertEquals(250, config.gameThreadTimeoutMillis());
    assertEquals(2, config.maxConcurrentRequests());
    assertEquals(1, config.enabledTools().size());
  }

  @Test
  void invalidExistingFilesAreRejectedWithoutBeingOverwritten() throws IOException {
    Path path = temporaryDirectory.resolve("thread.json");
    String invalid = "{\"schemaVersion\":1,\"unexpected\":true}";
    Files.writeString(path, invalid);

    assertThrows(IllegalArgumentException.class, () -> ThreadConfigLoader.loadOrCreate(path));
    assertEquals(invalid, Files.readString(path));
  }

  @Test
  void rejectsWrongTypesFractionalIntegersAndUnsafeValues() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ThreadConfigLoader.parse("{\"schemaVersion\":1,\"mcpEnabled\":\"yes\"}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ThreadConfigLoader.parse("{\"schemaVersion\":1,\"mcpPort\":25580.5}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> ThreadConfigLoader.parse("{\"schemaVersion\":1,\"mcpBindHost\":\"0.0.0.0\"}"));
  }
}
