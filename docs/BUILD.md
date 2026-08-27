# Thread Build

Thread keeps the compatibility axes visible in the filesystem and Gradle graph:

```text
common/
minecraft/
  shared/
  26.1.2/
  26.2/
loaders/
  fabric/{src,26.1.2,26.2}/
  neoforge/{src,26.1.2,26.2}/
  forge/{src,26.1.2,26.2}/
gradle/
```

`gradle/version-matrix.gradle` is the single source of truth for the Java version, supported
Minecraft versions, Fabric Loader/API versions, NeoForge versions, Forge versions, and supported
loader combinations. `settings.gradle` derives the nested projects from that matrix:

| Lane | Minecraft module | Fabric | NeoForge | Forge | Universal output task |
| --- | --- | --- | --- | --- | --- |
| 26.1.2 | `:minecraft:26.1.2` | `:loaders:fabric:26.1.2` | `:loaders:neoforge:26.1.2` | `:loaders:forge:26.1.2` | `universalJarMinecraft2612` |
| 26.2 | `:minecraft:26.2` | `:loaders:fabric:26.2` | `:loaders:neoforge:26.2` | `:loaders:forge:26.2` | `universalJarMinecraft262` |

`:common` contains core, configuration, integrations, MCP, and runtime/tool orchestration.
`minecraft/shared` contains Minecraft-facing sources that compile unchanged in every current
version lane; it is a shared source set, not a runtime module. The version directories contain only
real compile-time API/capability bindings and their version-specific tests. Each loader keeps one
shared thin source tree plus one small Gradle binding directory per version. No project or artifact
combines Minecraft versions.

## Common commands

Run commands from the repository root:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat test
.\gradlew.bat clean spotlessCheck check build verifyReleaseArtifact releaseBundle
```

Focused version compilation is also available:

```powershell
.\gradlew.bat :minecraft:26.1.2:test :minecraft:26.2:test
.\gradlew.bat :loaders:fabric:26.1.2:compileClientJava :loaders:fabric:26.2:compileClientJava
.\gradlew.bat :loaders:neoforge:26.1.2:compileJava :loaders:neoforge:26.2:compileJava
.\gradlew.bat :loaders:forge:26.1.2:compileJava :loaders:forge:26.2:compileJava
```

The architecture suite in `:common:test` rejects Minecraft dependencies in common, loader
dependencies in Minecraft sources, cross-version references, runtime version branching in shared
production Java, gameplay/provider logic in thin loader sources, stale flat modules, and unexpected
loader/version content in packaged JARs.

## Universal packaging and release outputs

Universal JARs are root packaging outputs registered by `gradle/universal-packaging.gradle`; there
is no universal source project per version. Each task packages common, one Minecraft lane, all three
matching loader outputs, and neutral universal metadata directly from the build matrix.

`releaseBundle` validates and writes eight installable JARs under `build/release/`, plus their
checksum files:

```text
thread-universal-26.1.2-<version>.jar
thread-fabric-26.1.2-<version>.jar
thread-neoforge-26.1.2-<version>.jar
thread-forge-26.1.2-<version>.jar
thread-universal-26.2-<version>.jar
thread-fabric-26.2-<version>.jar
thread-neoforge-26.2-<version>.jar
thread-forge-26.2-<version>.jar
```

Internal common and Minecraft-module JARs are not installable releases. Install exactly one
universal or dedicated JAR matching the instance's exact Minecraft version.

## Packaged client tests

Every nested loader/version project provides normal, restart, and MCP-disabled tasks for its
dedicated and matching universal artifact. Use a fully qualified project path; for example:

```powershell
.\gradlew.bat :loaders:fabric:26.1.2:runRestartProductionClientGameTest
.\gradlew.bat :loaders:fabric:26.2:runRestartProductionClientGameTest
.\gradlew.bat :loaders:neoforge:26.1.2:verifyUniversalNeoForgeRestartProductionClientGameTest
.\gradlew.bat :loaders:neoforge:26.2:verifyUniversalNeoForgeRestartProductionClientGameTest
.\gradlew.bat :loaders:forge:26.1.2:verifyForgeMcpDisabledProductionClientGameTest
.\gradlew.bat :loaders:forge:26.2:verifyForgeMcpDisabledProductionClientGameTest
```

All lanes compile the same loader-neutral parity fixture from `common/src/gametest/java`. See
[Development](DEVELOPMENT.md) for coverage and [Release](RELEASE.md) for the complete gate.
