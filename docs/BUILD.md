# Thread Build

Thread uses a Gradle multi-project build with six modules:

| Module | Purpose | Distributed |
| --- | --- | --- |
| `:common` | Version-neutral core, configuration, MCP, and runtime/tool orchestration | No |
| `:minecraft-26.2` | Loader-neutral Minecraft 26.2 access, mapping, providers, and capabilities | No |
| `:fabric` | Fabric entrypoint, Loader API access, events, metadata, and integration discovery | Yes |
| `:neoforge` | NeoForge entrypoint, Loader API access, events, metadata, and integration discovery | Yes |
| `:forge` | Forge entrypoint, Loader API access, events, metadata, and integration discovery | Yes |
| `:universal` | Packages common, Minecraft 26.2, and all three loader outputs | Yes |

`:minecraft-26.2` depends only on `:common`. All three loader modules consume both internal modules
and remain independent of one another. Each dedicated JAR merges common plus Minecraft 26.2 output
into one installable client mod. The packaging-only `:universal` module combines source-set outputs
directly and fails on unexpected duplicate entries.

## Common commands

Run commands from the repository root. Root tasks aggregate the appropriate module tasks:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat test
.\gradlew.bat clean spotlessCheck check build
```

Focused module commands are also available:

```powershell
.\gradlew.bat :common:test
.\gradlew.bat :minecraft-26.2:test
.\gradlew.bat :fabric:test
.\gradlew.bat :neoforge:test
.\gradlew.bat :forge:test
.\gradlew.bat :common:compileJava :minecraft-26.2:compileJava `
  :fabric:compileClientJava :neoforge:compileJava :forge:compileJava
```

The architecture suite runs in `:common:test` and inspects every source tree. It rejects Minecraft
imports/dependencies in common, loader imports/dependencies in `:minecraft-26.2`, gameplay imports
in loader adapters, Minecraft/loader imports in core or MCP, cross-loader imports, and production
Java outside each loader adapter package. Shared packaged assertions live under
`common/src/gametest/java`; all loader test source sets compile them into their separate proof JARs,
and the boundary suite verifies those fixtures contain no loader API imports.

## Outputs

Intermediate module artifacts are written under each module's `build/` directory. Installable
release artifacts are copied to:

```text
build/release/thread-universal-<version>.jar
build/release/thread-universal-<version>.jar.sha256
build/release/thread-fabric-<version>.jar
build/release/thread-fabric-<version>.jar.sha256
build/release/thread-neoforge-<version>.jar
build/release/thread-neoforge-<version>.jar.sha256
build/release/thread-forge-<version>.jar
build/release/thread-forge-<version>.jar.sha256
```

`common/build/libs/thread-common-<version>.jar` and
`minecraft-26.2/build/libs/thread-minecraft-26.2-<version>.jar` are internal build artifacts. Do not
install or publish them independently.

Install exactly one Thread JAR. The universal artifact is the default download; dedicated artifacts
remain available for modpacks, compatibility testing, and troubleshooting.

## Packaged client tests

Fabric packaged tests keep their existing root task names:

```powershell
.\gradlew.bat runProductionClientGameTest
.\gradlew.bat runRestartProductionClientGameTest
.\gradlew.bat runMcpDisabledProductionClientGameTest
```

These tasks launch the merged `thread-fabric-<version>.jar`, not either internal JAR or a
development source-set fallback. See [Development](DEVELOPMENT.md) for test coverage and
[Release](RELEASE.md) for the complete gate.

NeoForge has parallel packaged proofs:

```powershell
.\gradlew.bat verifyNeoForgeProductionClientGameTest
.\gradlew.bat verifyNeoForgeRestartProductionClientGameTest
.\gradlew.bat verifyNeoForgeMcpDisabledProductionClientGameTest
```

These stage `thread-neoforge-<version>.jar` plus a separately packaged external proof mod in a fresh
`mods` directory while explicitly loading no Thread development source set.

Forge has the same packaged proof set:

```powershell
.\gradlew.bat verifyForgeProductionClientGameTest
.\gradlew.bat verifyForgeRestartProductionClientGameTest
.\gradlew.bat verifyForgeMcpDisabledProductionClientGameTest
```

These stage `thread-forge-<version>.jar` plus its external proof mod. The production JAR embeds
common and Minecraft 26.2 once; both are kept off Forge's runtime classpath so ModLauncher sees one
Thread module.

The Fabric, NeoForge, and Forge launch tasks remain separate, but all execute the same loader-neutral
parity assertions for catalog, configuration, lifecycle state, MCP, tools, recipes, crafting, and
integration behavior.

The universal artifact has the same normal, restart, and MCP-disabled proofs on every loader:

```powershell
.\gradlew.bat verifyUniversalFabricProductionClientGameTest
.\gradlew.bat verifyUniversalFabricRestartProductionClientGameTest
.\gradlew.bat verifyUniversalFabricMcpDisabledProductionClientGameTest
.\gradlew.bat verifyUniversalNeoForgeProductionClientGameTest
.\gradlew.bat verifyUniversalNeoForgeRestartProductionClientGameTest
.\gradlew.bat verifyUniversalNeoForgeMcpDisabledProductionClientGameTest
.\gradlew.bat verifyUniversalForgeProductionClientGameTest
.\gradlew.bat verifyUniversalForgeRestartProductionClientGameTest
.\gradlew.bat verifyUniversalForgeMcpDisabledProductionClientGameTest
```
