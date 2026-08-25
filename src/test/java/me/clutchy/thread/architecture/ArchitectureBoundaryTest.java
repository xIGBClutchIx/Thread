package me.clutchy.thread.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {
  private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
  private static final List<Path> PRODUCTION_SOURCE_ROOTS =
      List.of(PROJECT_ROOT.resolve("src/main/java"), PROJECT_ROOT.resolve("src/client/java"));
  private static final Path CORE_MODEL_ROOT =
      PROJECT_ROOT.resolve("src/main/java/me/clutchy/thread/core/model");
  private static final Set<String> MODEL_DOMAINS =
      Set.of("capability", "crafting", "game", "item", "player", "recipe", "validation", "world");

  @Test
  void coreDoesNotImportPlatformOrTransportTypes() throws IOException {
    for (Path source : productionJavaSources()) {
      String path = normalizedPath(source);
      if (path.contains("/core/")) {
        String contents = Files.readString(source, StandardCharsets.UTF_8);
        assertFalse(contents.contains("net.fabricmc."), source::toString);
        assertFalse(contents.contains("net.minecraft."), source::toString);
        assertFalse(contents.contains("io.modelcontextprotocol."), source::toString);
      }
    }
  }

  @Test
  void mcpSdkTypesStayInsideTheMcpTransportPackage() throws IOException {
    for (Path source : productionJavaSources()) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      if (contents.contains("import io.modelcontextprotocol.")) {
        assertTrue(normalizedPath(source).contains("/transport/mcp/"), source::toString);
      }
    }
  }

  @Test
  void mcpTransportDoesNotReadMinecraftDirectly() throws IOException {
    for (Path source : productionJavaSources()) {
      if (normalizedPath(source).contains("/transport/mcp/")) {
        String contents = Files.readString(source, StandardCharsets.UTF_8);
        assertFalse(contents.contains("import net.fabricmc."), source::toString);
        assertFalse(contents.contains("import net.minecraft."), source::toString);
      }
    }
  }

  @Test
  void onlyTheSupportedJdkHttpServerUsesTheComSunNamespace() throws IOException {
    for (Path source : productionJavaSources()) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      List<String> comSunImports =
          contents
              .lines()
              .map(String::trim)
              .filter(line -> line.startsWith("import com.sun."))
              .toList();
      if (!comSunImports.isEmpty()) {
        assertTrue(normalizedPath(source).contains("/transport/mcp/"), source::toString);
        assertTrue(
            comSunImports.stream()
                .allMatch(line -> line.startsWith("import com.sun.net.httpserver.")),
            source::toString);
      }
    }
  }

  @Test
  void packageInfoFilesAreNotUsed() throws IOException {
    for (Path source : productionJavaSources()) {
      assertFalse(source.getFileName().toString().equals("package-info.java"), source::toString);
    }
  }

  @Test
  void coreModelsUseDomainPackages() throws IOException {
    try (Stream<Path> sources = Files.walk(CORE_MODEL_ROOT)) {
      for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = CORE_MODEL_ROOT.relativize(source);
        assertTrue(relative.getNameCount() > 1, source::toString);
        assertTrue(MODEL_DOMAINS.contains(relative.getName(0).toString()), source::toString);
      }
    }
  }

  @Test
  void optionalIntegrationCatalogUsesClassNamesInsteadOfClassLiterals() throws IOException {
    Path catalog =
        PROJECT_ROOT.resolve(
            "src/client/java/me/clutchy/thread/platform/fabric/integration/"
                + "FabricIntegrationCatalog.java");
    String contents = Files.readString(catalog, StandardCharsets.UTF_8);

    assertFalse(contents.contains(".class"), catalog::toString);
    assertFalse(contents.contains("ServiceLoader"), catalog::toString);
  }

  @Test
  void jeiApiTypesStayInsideTheJeiFabricIntegration() throws IOException {
    for (Path source : productionJavaSources()) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      if (contents.contains("import mezz.jei.")) {
        assertTrue(
            normalizedPath(source).contains("/platform/fabric/integration/jei/"), source::toString);
      }
    }
  }

  private static List<Path> productionJavaSources() throws IOException {
    try (Stream<Path> roots = PRODUCTION_SOURCE_ROOTS.stream()) {
      return roots
          .filter(Files::exists)
          .flatMap(ArchitectureBoundaryTest::walkUnchecked)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .toList();
    }
  }

  private static Stream<Path> walkUnchecked(Path root) {
    try {
      return Files.walk(root);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Could not inspect production source root: " + root, exception);
    }
  }

  private static String normalizedPath(Path path) {
    return path.toAbsolutePath().normalize().toString().replace('\\', '/');
  }
}
