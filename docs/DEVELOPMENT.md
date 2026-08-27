# Thread Development and Testing

This is the contributor checklist for source quality, local validation, packaged game tests, and
releases.

## Module workflow

Thread is built as `:common` plus thin `:fabric`, `:neoforge`, and `:forge` adapters. Shared core,
configuration, MCP, runtime assembly, and loader-neutral Minecraft providers belong in `:common`.
Loader API access, lifecycle events, metadata, and integration discovery stay in their adapter.
All three entrypoints call the same `ThreadRuntime.start` path after resolving those loader values.
The source-free `:universal` module packages these existing outputs and does not own runtime logic.

Root Gradle tasks aggregate all modules and remain the normal contributor interface. See
[Build](BUILD.md) for module-specific commands and outputs.

## Source standards

- Use Java 25 and the pinned Fabric, NeoForge, and Forge toolchains.
- Do not add Kotlin, `package-info.java`, wildcard imports, or hidden global state.
- Keep Minecraft, loader, core, and MCP dependencies inside their documented module boundaries.
- Apply the portability rule literally: code that another loader can invoke unchanged stays in
  common; code that calls a loader API stays in that loader's adapter.
- Prefer small immutable DTOs, explicit wiring, explicit schemas, and machine-readable errors.
- Keep every game query bounded and every gameplay path behind the single-player guard.
- Do not add a dependency when the JDK or Minecraft-provided runtime already supplies the narrow
  capability safely.

Public core contracts and supported extension points need useful Javadocs. Document snapshot/live
semantics, bounds, threading, absence, errors, and invariants when they are not obvious. Comments
should explain non-obvious lifecycle, logical-side, protocol, compatibility, or safety decisions;
do not narrate syntax or retain commented-out code.

Third-party adapters belong in separate Thread Integrations artifacts. Their metadata-only Fabric
entrypoint or NeoForge/Forge service provider must not link target-mod APIs. See
[Thread Integrations](INTEGRATIONS.md).

## Formatting and static checks

Spotless is the formatting entry point and uses pinned google-java-format for Java. Checkstyle uses
the small repository-owned ruleset under `config/checkstyle`. Normal Gradle lifecycle tasks include
formatting, lint, compiler, architecture, and unit-test checks.

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat spotlessCheck check build
```

CI checks formatting and never rewrites files. Do not disable or suppress a gate simply to make a
change pass; narrow a noisy rule only when the code has a documented legitimate reason.

## Test layers

### Unit and architecture tests

The root `test` task runs `:common:test`, `:fabric:test`, `:neoforge:test`, and `:forge:test`. They cover core
services, schemas, registries, configuration, integration activation/isolation, shared Minecraft
conversion/support, loader-specific discovery/version handling, and the real loopback MCP HTTP
server without launching Minecraft. Fake providers should prove core behavior whenever live
Minecraft is unnecessary.

Important regression areas include:

- success/error schema validation and deterministic discovery;
- all session states and multiplayer rejection;
- authoritative player vitals, game mode/hardcore, armor/air, bounded effects, movement and
  conditions, hotbar/cooldown, optional vehicle/respawn context, and deterministic serialization;
- world/environment identity, spawn-distance semantics, difficulty/hardcore, day/night and weather
  transitions, light/moon/climate fields, deterministic serialization, and loaded-world rejection;
- rich item/block/entity conversion and query caps;
- actual-look target-entity identity, interaction-distance limits, conditional living metadata,
  deterministic serialization, no-target behavior, and menu/multiplayer rejection;
- vanilla advancement visibility gating, completed/partial criterion progress, hierarchy and
  timestamps, filtering/search, deterministic ordering, truncation, and session rejection;
- nearby loaded-container ordering, occupancy summaries, full single-position inspection,
  unresolved-loot safety, and radius/result caps;
- unified live item matching, cross-source count aggregation, player/equipment location handling,
  deterministic source order, unresolved/unloaded container exclusion, and crafting isolation;
- live recipe variants, alternatives, and provider fallback;
- maximum-flow crafting allocation without inventory double-counting;
- recursive plans, shared supplies, cycles, safe alternatives, and work limits;
- metadata-first integration loading and transactional contribution rollback;
- MCP initialize and stateless discovery, protocol validation, request/origin limits, shutdown, and
  same-port restart;
- package/import and release-artifact boundaries.
- loader-to-common module direction, cross-loader isolation, and the absence of Fabric/NeoForge/Forge
  imports in common or the shared packaged parity fixture.

Run focused unit coverage with:

```powershell
.\gradlew.bat test
```

### Development client game test

```powershell
.\gradlew.bat runClientGameTest
```

This uses the development classpath for interactive diagnosis. It is useful during provider work
but is not the release proof.

### Packaged-client tests

```powershell
.\gradlew.bat runProductionClientGameTest
.\gradlew.bat runRestartProductionClientGameTest
.\gradlew.bat runMcpDisabledProductionClientGameTest
.\gradlew.bat verifyNeoForgeProductionClientGameTest
.\gradlew.bat verifyNeoForgeRestartProductionClientGameTest
.\gradlew.bat verifyNeoForgeMcpDisabledProductionClientGameTest
.\gradlew.bat verifyForgeProductionClientGameTest
.\gradlew.bat verifyForgeRestartProductionClientGameTest
.\gradlew.bat verifyForgeMcpDisabledProductionClientGameTest
.\gradlew.bat verifyUniversalFabricRestartProductionClientGameTest
.\gradlew.bat verifyUniversalFabricMcpDisabledProductionClientGameTest
.\gradlew.bat verifyUniversalNeoForgeRestartProductionClientGameTest
.\gradlew.bat verifyUniversalNeoForgeMcpDisabledProductionClientGameTest
.\gradlew.bat verifyUniversalForgeRestartProductionClientGameTest
.\gradlew.bat verifyUniversalForgeMcpDisabledProductionClientGameTest
```

Each normal test launches a temporary client with a final dedicated or universal Thread JAR and a
separately packaged proof integration/game-test mod. All three loaders compile the same
loader-neutral parity fixture from `common/src/gametest/java`. It verifies the exact twenty-tool catalog, config,
loader identity, menu/world/menu status, MCP initialization and discovery, every tool path, native
recipes and crafting, external integration activation, and controlled gameplay rejection at the
menu.

Launch control remains loader-specific because the APIs are genuinely different: Fabric uses the
Fabric client game-test context, while NeoForge and Forge use bounded event-driven state machines.
Fabric also retains richer deterministic payload assertions for player-state transitions, inventory,
equipment, target blocks, nearby entities, and actual-look target entities with hostile,
non-living, tame-owner, and villager variants; those are provider regression coverage, not a
different loader contract.

The restart test launches that packaged client again from the same instance and proves an existing
configuration is reloaded, including a changed MCP port. The disabled tests use separate fresh
instances and prove Thread initializes without a listener. None of these tasks
may silently fall back to the source-set development classpath.

## Full local gate

Before committing a V1-complete change, run the release-equivalent gate from the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain clean spotlessApply spotlessCheck check build `
  verifyReleaseArtifact releaseBundle verifyReleaseVersion "-PreleaseTag=v0.1.0"
.\gradlew.bat --no-daemon --console=plain runRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain runMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyNeoForgeRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyNeoForgeMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyForgeRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyForgeMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalFabricRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalFabricMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalNeoForgeRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalNeoForgeMcpDisabledProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalForgeRestartProductionClientGameTest
.\gradlew.bat --no-daemon --console=plain verifyUniversalForgeMcpDisabledProductionClientGameTest
git diff --check
```

Keep each packaged task in its own Gradle invocation. Loader launch tasks use real client processes
and must not overlap on the shared MCP port or test-instance preparation.

Change the release tag argument when `mod_version` changes. Release versions and tags must use
`MAJOR.MINOR.PATCH` and `vMAJOR.MINOR.PATCH`, respectively. `verifyReleaseArtifact` rejects test
classes, source files, and bundled third-party integrations while checking common contracts,
shared runtime/provider classes, dedicated loader purity, universal loader coverage, and metadata.
`releaseBundle` writes the universal and three dedicated JARs plus SHA-256 files to
`build/release/`. See
[Release](RELEASE.md) for the publishing checklist.

## Manual MCP smoke test

Use the release JAR, not a development run, for the final human check:

1. Put the universal release JAR and its loader requirements in a clean Minecraft 26.2 instance.
2. Launch to the menu and confirm `http://127.0.0.1:25580/mcp` is listening.
3. Connect a real MCP client, complete `initialize` followed by `tools/list`, and confirm twenty
   read-only `minecraft.*` tools.
4. Call `minecraft.get_status` in the menu; it must return a controlled unsupported/no-world state.
5. Load an integrated single-player world and call status, player, world info, inventory, equipment,
   target block, target entity, nearby-entity, nearby-container, container-inspection, unified live item search, vanilla
   advancement list/detail, recipe, crafting, registry search, and capability tools.
6. Confirm tools return detached bounded data, recipes reflect the live world, container searches
   skip unloaded chunks, and no call mutates the game. Confirm crafting with omitted or explicit
   `PLAYER_ONLY` scope ignores nearby-only items, while explicit `PLAYER_AND_NEARBY` can use an
   eligible loaded container and reports its source plus any incomplete discovery.
7. Join multiplayer only for rejection verification if appropriate: status remains callable while
   gameplay tools return `UNSUPPORTED` without exposing live state.
8. Exit Minecraft and confirm the listener closes cleanly.

Automated HTTP, packaged-world, disabled-listener, boundary, and limit checks remain authoritative;
the manual smoke test verifies actual client interoperability and installation behavior.

## Release process

The authoritative release gate, artifact layout, and publishing checklist are documented in
[Release](RELEASE.md).

## CI

`.github/workflows/ci.yml` runs on pull requests, primary-branch pushes, and manual dispatch.
`.github/workflows/release.yml` handles `v*` tags. Both use the Gradle Wrapper and the same project
tasks documented above; local work must not rely on a hidden alternative validation path.
