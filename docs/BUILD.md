# Thread Build

Thread uses a Gradle multi-project build with two modules:

| Module | Purpose | Distributed |
| --- | --- | --- |
| `:common` | Core, configuration, MCP, shared runtime, and loader-neutral Minecraft providers | No |
| `:fabric` | Fabric entrypoint, Loader API access, events, metadata, and integration discovery | Yes |

`:fabric` depends on `:common`. Its JAR task merges common output into one installable client mod;
the build does not create a universal multi-loader JAR.

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
.\gradlew.bat :common:compileJava :fabric:compileClientJava
```

The architecture suite runs in `:common:test` and inspects both source trees. It rejects Fabric
imports in common, Minecraft/loader imports in core or MCP, and production Java outside the Fabric
adapter package in `:fabric`.

## Outputs

Intermediate module artifacts are written under each module's `build/` directory. The only
installable release artifact is copied to:

```text
build/release/thread-fabric-<version>.jar
build/release/thread-fabric-<version>.jar.sha256
```

`common/build/libs/thread-common-<version>.jar` is an internal build artifact. Do not install or
publish it independently.

## Packaged client tests

The root keeps the existing task names while delegating to the Fabric module:

```powershell
.\gradlew.bat runProductionClientGameTest
.\gradlew.bat runRestartProductionClientGameTest
.\gradlew.bat runMcpDisabledProductionClientGameTest
```

These tasks launch the merged `thread-fabric-<version>.jar`, not a common JAR or development
source-set fallback. See [Development](DEVELOPMENT.md) for test coverage and [Release](RELEASE.md)
for the complete gate.
