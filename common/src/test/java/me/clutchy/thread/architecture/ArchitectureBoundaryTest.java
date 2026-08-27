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
  private static final Path MINECRAFT_SHARED_SOURCE_ROOT =
      PROJECT_ROOT.resolve("minecraft/shared/src/main/java");
  private static final Path MINECRAFT_2612_SOURCE_ROOT =
      PROJECT_ROOT.resolve("minecraft/26.1.2/src/main/java");
  private static final Path MINECRAFT_262_SOURCE_ROOT =
      PROJECT_ROOT.resolve("minecraft/26.2/src/main/java");
  private static final List<Path> MINECRAFT_SOURCE_ROOTS =
      List.of(MINECRAFT_SHARED_SOURCE_ROOT, MINECRAFT_2612_SOURCE_ROOT, MINECRAFT_262_SOURCE_ROOT);
  private static final Path SHARED_GAMETEST_ROOT = PROJECT_ROOT.resolve("common/src/gametest/java");
  private static final List<Path> FABRIC_SOURCE_ROOTS =
      List.of(
          PROJECT_ROOT.resolve("loaders/fabric/src/main/java"),
          PROJECT_ROOT.resolve("loaders/fabric/src/client/java"));
  private static final Path NEOFORGE_SOURCE_ROOT =
      PROJECT_ROOT.resolve("loaders/neoforge/src/main/java");
  private static final Path FORGE_SOURCE_ROOT = PROJECT_ROOT.resolve("loaders/forge/src/main/java");
  private static final List<Path> PRODUCTION_SOURCE_ROOTS =
      Stream.concat(
              Stream.of(
                  COMMON_SOURCE_ROOT,
                  MINECRAFT_SHARED_SOURCE_ROOT,
                  MINECRAFT_2612_SOURCE_ROOT,
                  MINECRAFT_262_SOURCE_ROOT,
                  NEOFORGE_SOURCE_ROOT,
                  FORGE_SOURCE_ROOT),
              FABRIC_SOURCE_ROOTS.stream())
          .toList();
  private static final Path CORE_MODEL_ROOT =
      COMMON_SOURCE_ROOT.resolve("me/clutchy/thread/core/model");
  private static final Set<String> MODEL_DOMAINS =
      Set.of(
          "advancement",
          "capability",
          "crafting",
          "game",
          "item",
          "options",
          "player",
          "recipe",
          "validation",
          "world");

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
  void versionNeutralCommonDoesNotImportMinecraftOrLoaderApis() throws IOException {
    for (Path source : javaSourcesUnder(COMMON_SOURCE_ROOT)) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertFalse(contents.contains("net.minecraft."), source::toString);
      assertFalse(contents.contains("import net.fabricmc."), source::toString);
      assertFalse(contents.contains("import net.neoforged."), source::toString);
      assertFalse(contents.contains("import net.minecraftforge."), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.fabric"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.neoforge"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.forge"), source::toString);
    }

    String commonBuild =
        Files.readString(PROJECT_ROOT.resolve("common/build.gradle"), StandardCharsets.UTF_8);
    assertFalse(commonBuild.contains("fabric-loom"), "common must not apply Fabric Loom");
    assertFalse(
        commonBuild.contains("com.mojang:minecraft"), "common must not depend on Minecraft");
    assertFalse(commonBuild.contains("fabric-loader"), "common must not depend on Fabric Loader");
    assertFalse(commonBuild.contains("fabric-api"), "common must not depend on Fabric API");
    assertFalse(commonBuild.contains("neoforge"), "common must not depend on NeoForge");
    assertFalse(commonBuild.contains("net.minecraftforge"), "common must not depend on Forge");
  }

  @Test
  void minecraftVersionModulesDoNotImportOrDependOnLoaderApis() throws IOException {
    for (Path source : javaSourcesUnder(MINECRAFT_SOURCE_ROOTS)) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertFalse(contents.contains("import net.fabricmc."), source::toString);
      assertFalse(contents.contains("import net.neoforged."), source::toString);
      assertFalse(contents.contains("import net.minecraftforge."), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.fabric"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.neoforge"), source::toString);
      assertFalse(contents.contains("me.clutchy.thread.platform.forge"), source::toString);
    }

    String minecraftConvention =
        Files.readString(
            PROJECT_ROOT.resolve("gradle/minecraft-module.gradle"), StandardCharsets.UTF_8);
    assertTrue(
        minecraftConvention.contains("implementation project(':common')"),
        "Minecraft convention must consume common");
    assertFalse(minecraftConvention.contains("fabric-loader"));
    assertFalse(minecraftConvention.contains("fabric-api"));
    assertFalse(minecraftConvention.contains("neoforge"));
    assertFalse(minecraftConvention.contains("net.minecraftforge"));

    for (String version : List.of("26.1.2", "26.2")) {
      String minecraftBuild =
          Files.readString(
              PROJECT_ROOT.resolve("minecraft/" + version + "/build.gradle"),
              StandardCharsets.UTF_8);
      assertTrue(
          minecraftBuild.contains("gradle/minecraft-module.gradle"),
          "Minecraft " + version + " must use the shared convention");
      assertFalse(minecraftBuild.contains("fabric-loader"), version);
      assertFalse(minecraftBuild.contains("fabric-api"), version);
      assertFalse(minecraftBuild.contains("neoforge"), version);
      assertFalse(minecraftBuild.contains("net.minecraftforge"), version);
    }
  }

  @Test
  void moduleLayoutIsExplicitAndOneWay() throws IOException {
    String settings =
        Files.readString(PROJECT_ROOT.resolve("settings.gradle"), StandardCharsets.UTF_8);
    String versionMatrix =
        Files.readString(
            PROJECT_ROOT.resolve("gradle/version-matrix.gradle"), StandardCharsets.UTF_8);
    String commonBuild =
        Files.readString(PROJECT_ROOT.resolve("common/build.gradle"), StandardCharsets.UTF_8);
    String universalPackaging =
        Files.readString(
            PROJECT_ROOT.resolve("gradle/universal-packaging.gradle"), StandardCharsets.UTF_8);

    assertTrue(settings.contains("gradle/version-matrix.gradle"));
    assertTrue(settings.contains(":minecraft:${minecraftVersion}"));
    assertTrue(settings.contains(":loaders:${loader}:${minecraftVersion}"));
    assertTrue(settings.contains("file(\"minecraft/${minecraftVersion}\")"));
    assertTrue(settings.contains("file(\"loaders/${loader}/${minecraftVersion}\")"));

    for (String version : List.of("26.1.2", "26.2")) {
      assertTrue(versionMatrix.contains("'" + version + "'"), version);
      assertTrue(Files.isDirectory(PROJECT_ROOT.resolve("minecraft/" + version)), version);
      for (String loader : List.of("fabric", "neoforge", "forge")) {
        assertTrue(
            Files.isDirectory(PROJECT_ROOT.resolve("loaders/" + loader + "/" + version)),
            loader + " " + version);
      }
    }

    assertTrue(Files.isDirectory(PROJECT_ROOT.resolve("minecraft/shared/src/main/java")));
    assertFalse(commonBuild.contains("project(':minecraft:"));
    assertFalse(commonBuild.contains("project(':loaders:"));
    assertTrue(
        universalPackaging.contains("duplicatesStrategy = DuplicatesStrategy.FAIL"),
        "universal must fail on unexpected duplicate entries");
    assertTrue(universalPackaging.contains("threadBuildMatrix.versions.each"));
    assertFalse(
        Files.exists(PROJECT_ROOT.resolve("universal/build.gradle")),
        "universal artifacts must be matrix-driven packaging outputs");
    assertFalse(Files.exists(PROJECT_ROOT.resolve("universal-26.1.2/build.gradle")));
    assertFalse(
        Files.exists(PROJECT_ROOT.resolve("src")), "legacy root source tree must stay absent");
  }

  @Test
  void sharedProductionCodeDoesNotBranchOnMinecraftVersion() throws IOException {
    for (Path source :
        javaSourcesUnder(List.of(COMMON_SOURCE_ROOT, MINECRAFT_SHARED_SOURCE_ROOT))) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertFalse(contents.contains("26.1.2"), source::toString);
      assertFalse(contents.contains("26.2"), source::toString);
    }
  }

  @Test
  void versionLanesDoNotReferenceEachOther() throws IOException {
    assertVersionLaneIsolation(MINECRAFT_2612_SOURCE_ROOT, "26.2", "v26_2");
    assertVersionLaneIsolation(MINECRAFT_262_SOURCE_ROOT, "26.1.2", "v26_1_2");

    for (String version : List.of("26.1.2", "26.2")) {
      for (String loader : List.of("fabric", "neoforge", "forge")) {
        String binding =
            Files.readString(
                PROJECT_ROOT.resolve("loaders/" + loader + "/" + version + "/build.gradle"),
                StandardCharsets.UTF_8);
        assertTrue(binding.contains("ext.threadVersion = project.name"), binding);
        assertFalse(binding.contains(version.equals("26.1.2") ? "26.2" : "26.1.2"), binding);
      }
    }
  }

  @Test
  void fabricProductionCodeStaysInsideTheFabricAdapterPackage() throws IOException {
    for (Path source : javaSourcesUnder(FABRIC_SOURCE_ROOTS)) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertTrue(
          normalizedPath(source).contains("/me/clutchy/thread/platform/fabric/"), source::toString);
      assertFalse(contents.contains("import net.neoforged."), source::toString);
      assertFalse(contents.contains("import net.minecraftforge."), source::toString);
      assertThinLoaderSource(contents, source);
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
      assertThinLoaderSource(contents, source);
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
      assertThinLoaderSource(contents, source);
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
        Files.readString(
            PROJECT_ROOT.resolve("gradle/fabric-module.gradle"), StandardCharsets.UTF_8);
    String neoForgeBuild =
        Files.readString(
            PROJECT_ROOT.resolve("gradle/neoforge-module.gradle"), StandardCharsets.UTF_8);
    String forgeBuild =
        Files.readString(
            PROJECT_ROOT.resolve("gradle/forge-module.gradle"), StandardCharsets.UTF_8);
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
  void craftingServicesConsumeOnlyDetachedItemSourceSnapshots() throws IOException {
    for (String service : List.of("CraftingService.java", "CraftingPlanner.java")) {
      Path source = COMMON_SOURCE_ROOT.resolve("me/clutchy/thread/core/service").resolve(service);
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertFalse(contents.contains("WorldProvider"), source::toString);
      assertFalse(contents.contains("NearbyContainer"), source::toString);
      assertFalse(contents.contains("ContainerInspection"), source::toString);
      assertFalse(contents.contains("ItemFinder"), source::toString);
      assertFalse(contents.contains("find_item"), source::toString);
      assertTrue(contents.contains("CraftingItemSourceProvider"), source::toString);
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
            "loaders/fabric/src/client/java/me/clutchy/thread/platform/fabric/integration/"
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
            PROJECT_ROOT.resolve("minecraft/26.1.2/build.gradle"),
            PROJECT_ROOT.resolve("minecraft/26.2/build.gradle"),
            PROJECT_ROOT.resolve("loaders/fabric/26.1.2/build.gradle"),
            PROJECT_ROOT.resolve("loaders/fabric/26.2/build.gradle"),
            PROJECT_ROOT.resolve("loaders/neoforge/26.1.2/build.gradle"),
            PROJECT_ROOT.resolve("loaders/neoforge/26.2/build.gradle"),
            PROJECT_ROOT.resolve("loaders/forge/26.1.2/build.gradle"),
            PROJECT_ROOT.resolve("loaders/forge/26.2/build.gradle"),
            PROJECT_ROOT.resolve("gradle/version-matrix.gradle"),
            PROJECT_ROOT.resolve("gradle/minecraft-module.gradle"),
            PROJECT_ROOT.resolve("gradle/fabric-module.gradle"),
            PROJECT_ROOT.resolve("gradle/neoforge-module.gradle"),
            PROJECT_ROOT.resolve("gradle/forge-module.gradle"),
            PROJECT_ROOT.resolve("gradle/universal-packaging.gradle"),
            PROJECT_ROOT.resolve("gradle.properties"),
            PROJECT_ROOT.resolve("loaders/fabric/src/main/resources/fabric.mod.json"),
            PROJECT_ROOT.resolve("loaders/neoforge/src/main/resources/META-INF/neoforge.mods.toml"),
            PROJECT_ROOT.resolve("loaders/forge/src/main/resources/META-INF/mods.toml"))) {
      String contents = Files.readString(artifactInput, StandardCharsets.UTF_8).toLowerCase();
      assertFalse(contents.contains("jei"), artifactInput::toString);
    }
  }

  private static List<Path> productionJavaSources() throws IOException {
    return javaSourcesUnder(PRODUCTION_SOURCE_ROOTS);
  }

  private static void assertVersionLaneIsolation(
      Path sourceRoot, String forbiddenVersion, String forbiddenPackage) throws IOException {
    for (Path source : javaSourcesUnder(sourceRoot)) {
      String contents = Files.readString(source, StandardCharsets.UTF_8);
      assertFalse(contents.contains(forbiddenVersion), source::toString);
      assertFalse(contents.contains(forbiddenPackage), source::toString);
    }
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

  private static void assertThinLoaderSource(String contents, Path source) {
    assertFalse(contents.contains("net.minecraft."), source::toString);
    assertFalse(contents.contains("me.clutchy.thread.platform.minecraft"), source::toString);
    assertFalse(contents.contains("me.clutchy.thread.core.provider"), source::toString);
    assertFalse(contents.contains("me.clutchy.thread.core.service"), source::toString);
  }
}
