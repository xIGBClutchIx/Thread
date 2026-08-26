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
  private static final Path PROJECT_ROOT =
      Path.of(System.getProperty("thread.rootDir")).toAbsolutePath().normalize();
  private static final Path COMMON_SOURCE_ROOT = PROJECT_ROOT.resolve("common/src/main/java");
  private static final Path SHARED_GAMETEST_ROOT = PROJECT_ROOT.resolve("common/src/gametest/java");
  private static final List<Path> FABRIC_SOURCE_ROOTS =
      List.of(
          PROJECT_ROOT.resolve("fabric/src/main/java"),
          PROJECT_ROOT.resolve("fabric/src/client/java"));
  private static final Path NEOFORGE_SOURCE_ROOT = PROJECT_ROOT.resolve("neoforge/src/main/java");
  private static final Path FORGE_SOURCE_ROOT = PROJECT_ROOT.resolve("forge/src/main/java");
  private static final List<Path> PRODUCTION_SOURCE_ROOTS =
      Stream.concat(
              Stream.concat(Stream.of(COMMON_SOURCE_ROOT), FABRIC_SOURCE_ROOTS.stream()),
              Stream.of(NEOFORGE_SOURCE_ROOT, FORGE_SOURCE_ROOT))
          .toList();
  private static final Path CORE_MODEL_ROOT =
      COMMON_SOURCE_ROOT.resolve("me/clutchy/thread/core/model");
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
  void commonModuleDoesNotImportLoaderApis() throws IOException {
    for (Path source : javaSourcesUnder(COMMON_SOURCE_ROOT)) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertFalse(contents.contains("import net.fabricmc."), source::toString);
      assertFalse(contents.contains("import net.neoforged."), source::toString);
      assertFalse(contents.contains("import net.minecraftforge."), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.fabric"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.neoforge"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.forge"), source::toString);
    }

    String commonBuild =
        Files.readString(PROJECT_ROOT.resolve("common/build.gradle"), StandardCharsets.UTF_8);
    assertFalse(commonBuild.contains("fabric-loader"), "common must not depend on Fabric Loader");
    assertFalse(commonBuild.contains("fabric-api"), "common must not depend on Fabric API");
    assertFalse(commonBuild.contains("neoforge"), "common must not depend on NeoForge");
    assertFalse(commonBuild.contains("net.minecraftforge"), "common must not depend on Forge");
  }

  @Test
  void moduleLayoutIsExplicitAndOneWay() throws IOException {
    String settings =
        Files.readString(PROJECT_ROOT.resolve("settings.gradle"), StandardCharsets.UTF_8);
    String commonBuild =
        Files.readString(PROJECT_ROOT.resolve("common/build.gradle"), StandardCharsets.UTF_8);
    String fabricBuild =
        Files.readString(PROJECT_ROOT.resolve("fabric/build.gradle"), StandardCharsets.UTF_8);
    String neoForgeBuild =
        Files.readString(PROJECT_ROOT.resolve("neoforge/build.gradle"), StandardCharsets.UTF_8);
    String forgeBuild =
        Files.readString(PROJECT_ROOT.resolve("forge/build.gradle"), StandardCharsets.UTF_8);
    String universalBuild =
        Files.readString(PROJECT_ROOT.resolve("universal/build.gradle"), StandardCharsets.UTF_8);

    assertTrue(
        settings.contains("include 'common', 'fabric', 'neoforge', 'forge', 'universal'"),
        "settings must declare all modules");
    assertTrue(fabricBuild.contains("project(':common')"), "Fabric must consume common");
    assertTrue(neoForgeBuild.contains("project(':common')"), "NeoForge must consume common");
    assertTrue(forgeBuild.contains("project(':common')"), "Forge must consume common");
    assertFalse(commonBuild.contains("project(':fabric')"), "common must not consume Fabric");
    assertFalse(commonBuild.contains("project(':neoforge')"), "common must not consume NeoForge");
    assertFalse(commonBuild.contains("project(':forge')"), "common must not consume Forge");
    assertFalse(fabricBuild.contains("project(':neoforge')"), "Fabric must not consume NeoForge");
    assertFalse(fabricBuild.contains("project(':forge')"), "Fabric must not consume Forge");
    assertFalse(neoForgeBuild.contains("project(':fabric')"), "NeoForge must not consume Fabric");
    assertFalse(neoForgeBuild.contains("project(':forge')"), "NeoForge must not consume Forge");
    assertFalse(forgeBuild.contains("project(':fabric')"), "Forge must not consume Fabric");
    assertFalse(forgeBuild.contains("project(':neoforge')"), "Forge must not consume NeoForge");
    assertTrue(universalBuild.contains("project(':common')"), "universal must package common");
    assertTrue(universalBuild.contains("project(':fabric')"), "universal must package Fabric");
    assertTrue(universalBuild.contains("project(':neoforge')"), "universal must package NeoForge");
    assertTrue(universalBuild.contains("project(':forge')"), "universal must package Forge");
    assertTrue(
        universalBuild.contains("duplicatesStrategy = DuplicatesStrategy.FAIL"),
        "universal must fail on unexpected duplicate entries");
    assertFalse(
        Files.exists(PROJECT_ROOT.resolve("universal/src/main/java")),
        "universal must remain packaging-only");
    assertFalse(
        Files.exists(PROJECT_ROOT.resolve("src")), "legacy root source tree must stay absent");
  }

  @Test
  void fabricProductionCodeStaysInsideTheFabricAdapterPackage() throws IOException {
    for (Path source : javaSourcesUnder(FABRIC_SOURCE_ROOTS)) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertTrue(
          normalizedPath(source).contains("/me/clutchy/thread/platform/fabric/"), source::toString);
      assertFalse(contents.contains("import net.neoforged."), source::toString);
      assertFalse(contents.contains("import net.minecraftforge."), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.neoforge"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.forge"), source::toString);
    }
  }

  @Test
  void neoForgeProductionCodeStaysInsideTheNeoForgeAdapterPackage() throws IOException {
    for (Path source : javaSourcesUnder(NEOFORGE_SOURCE_ROOT)) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertTrue(
          normalizedPath(source).contains("/me/clutchy/thread/platform/neoforge/"),
          source::toString);
      assertFalse(contents.contains("import net.fabricmc."), source::toString);
      assertFalse(contents.contains("import net.minecraftforge."), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.fabric"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.forge"), source::toString);
    }
  }

  @Test
  void forgeProductionCodeStaysInsideTheForgeAdapterPackage() throws IOException {
    for (Path source : javaSourcesUnder(FORGE_SOURCE_ROOT)) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertTrue(
          normalizedPath(source).contains("/me/clutchy/thread/platform/forge/"), source::toString);
      assertFalse(contents.contains("import net.fabricmc."), source::toString);
      assertFalse(contents.contains("import net.neoforged."), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.fabric"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.neoforge"), source::toString);
    }
  }

  @Test
  void sharedPackagedParityCodeDoesNotImportLoaderApis() throws IOException {
    for (Path source : javaSourcesUnder(SHARED_GAMETEST_ROOT)) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertFalse(contents.contains("import net.fabricmc."), source::toString);
      assertFalse(contents.contains("import net.neoforged."), source::toString);
      assertFalse(contents.contains("import net.minecraftforge."), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.fabric"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.neoforge"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.forge"), source::toString);
    }

    String fabricBuild =
        Files.readString(PROJECT_ROOT.resolve("fabric/build.gradle"), StandardCharsets.UTF_8);
    String neoForgeBuild =
        Files.readString(PROJECT_ROOT.resolve("neoforge/build.gradle"), StandardCharsets.UTF_8);
    String forgeBuild =
        Files.readString(PROJECT_ROOT.resolve("forge/build.gradle"), StandardCharsets.UTF_8);
    assertTrue(
        fabricBuild.contains("common/src/gametest/java"),
        "Fabric packaged tests must compile the shared parity contract");
    assertTrue(
        neoForgeBuild.contains("common/src/gametest/java"),
        "NeoForge packaged tests must compile the shared parity contract");
    assertTrue(
        forgeBuild.contains("common/src/gametest/java"),
        "Forge packaged tests must compile the shared parity contract");
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
        assertFalse(contents.contains("import net.neoforged."), source::toString);
        assertFalse(contents.contains("import net.minecraftforge."), source::toString);
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
  void optionalIntegrationCatalogDoesNotReferenceImplementationClassLiterals() throws IOException {
    Path catalog =
        PROJECT_ROOT.resolve(
            "fabric/src/client/java/me/clutchy/thread/platform/fabric/integration/"
                + "FabricIntegrationCatalog.java");
    String contents = Files.readString(catalog, StandardCharsets.UTF_8);

    assertFalse(
        contents
            .replace("FabricIntegrationCatalog.class", "")
            .replace("ThreadIntegrationCandidateProvider.class", "")
            .contains(".class"),
        catalog::toString);
    assertFalse(contents.contains("ServiceLoader"), catalog::toString);
  }

  @Test
  void baseArtifactHasNoBundledRecipeViewerImplementationOrDependency() throws IOException {
    for (Path source : productionJavaSources()) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertFalse(contents.contains("mezz.jei"), source::toString);
      assertFalse(normalizedPath(source).contains("/integration/jei/"), source::toString);
    }

    for (Path artifactInput :
        List.of(
            PROJECT_ROOT.resolve("build.gradle"),
            PROJECT_ROOT.resolve("common/build.gradle"),
            PROJECT_ROOT.resolve("fabric/build.gradle"),
            PROJECT_ROOT.resolve("neoforge/build.gradle"),
            PROJECT_ROOT.resolve("forge/build.gradle"),
            PROJECT_ROOT.resolve("universal/build.gradle"),
            PROJECT_ROOT.resolve("gradle.properties"),
            PROJECT_ROOT.resolve("fabric/src/main/resources/fabric.mod.json"),
            PROJECT_ROOT.resolve("neoforge/src/main/resources/META-INF/neoforge.mods.toml"),
            PROJECT_ROOT.resolve("forge/src/main/resources/META-INF/mods.toml"))) {
      String contents = Files.readString(artifactInput, StandardCharsets.UTF_8).toLowerCase();
      assertFalse(contents.contains("jei"), artifactInput::toString);
    }
  }

  private static List<Path> productionJavaSources() throws IOException {
    return javaSourcesUnder(PRODUCTION_SOURCE_ROOTS);
  }

  private static List<Path> javaSourcesUnder(Path root) throws IOException {
    return javaSourcesUnder(List.of(root));
  }

  private static List<Path> javaSourcesUnder(List<Path> roots) throws IOException {
    try (Stream<Path> sourceRoots = roots.stream()) {
      return sourceRoots
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
