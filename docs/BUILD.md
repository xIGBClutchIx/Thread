# Thread Build

Thread uses a Gradle multi-project build with three modules:

| Module | Purpose | Distributed |
| --- | --- | --- |
| `:common` | Core, configuration, MCP, shared runtime, and loader-neutral Minecraft providers | No |
| `:fabric` | Fabric entrypoint, Loader API access, events, metadata, and integration discovery | Yes |
| `:neoforge` | NeoForge entrypoint, Loader API access, events, metadata, and integration discovery | Yes |

Both loader modules depend on `:common`. Each JAR task merges common output into one installable
client mod; the build does not create a universal multi-loader JAR.

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
.\gradlew.bat :fabric:test
.\gradlew.bat :neoforge:test
.\gradlew.bat :common:compileJava :fabric:compileClientJava :neoforge:compileJava
```

The architecture suite runs in `:common:test` and inspects every source tree. It rejects loader
imports in common, Minecraft/loader imports in core or MCP, cross-loader imports, and production
Java outside each loader adapter package. Shared packaged assertions live under
`common/src/gametest/java`; both loader test source sets compile them into their separate proof JARs,
and the boundary suite verifies those fixtures contain no loader API imports.

## Outputs

Intermediate module artifacts are written under each module's `build/` directory. Installable
release artifacts are copied to:

```text
build/release/thread-fabric-<version>.jar
build/release/thread-fabric-<version>.jar.sha256
build/release/thread-neoforge-<version>.jar
build/release/thread-neoforge-<version>.jar.sha256
```

`common/build/libs/thread-common-<version>.jar` is an internal build artifact. Do not install or
publish it independently.

## Packaged client tests

Fabric packaged tests keep their existing root task names:

```powershell
.\gradlew.bat runProductionClientGameTest
.\gradlew.bat runRestartProductionClientGameTest
.\gradlew.bat runMcpDisabledProductionClientGameTest
```

These tasks launch the merged `thread-fabric-<version>.jar`, not a common JAR or development
source-set fallback. See [Development](DEVELOPMENT.md) for test coverage and [Release](RELEASE.md)
for the complete gate.

NeoForge has parallel packaged proofs:

```powershell
.\gradlew.bat verifyNeoForgeProductionClientGameTest
.\gradlew.bat verifyNeoForgeRestartProductionClientGameTest
.\gradlew.bat verifyNeoForgeMcpDisabledProductionClientGameTest
```

These stage `thread-neoforge-<version>.jar` plus a separately packaged external proof mod in a fresh
`mods` directory while explicitly loading no Thread development source set.

The Fabric and NeoForge launch tasks remain separate, but both execute the same loader-neutral
parity assertions for catalog, configuration, lifecycle state, MCP, tools, recipes, crafting, and
integration behavior.
