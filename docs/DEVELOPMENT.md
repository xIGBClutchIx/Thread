# Thread Development and Testing

This is the contributor checklist for source quality, local validation, packaged game tests, and
releases.

## Module workflow

Thread is built across independent Minecraft-version and loader axes. `:common` owns
version-neutral core, configuration, MCP, and runtime/tool orchestration. `minecraft/shared`
contains Minecraft-facing sources that compile independently in `:minecraft:1.21.11`,
`:minecraft:26.1.2`, and `:minecraft:26.2`; only real API/capability bindings remain in the
version directories. Thin
`:loaders:<loader>:<version>` projects compile one loader's shared source tree against exactly one
Minecraft lane. Each invokes the lane-compiled `MinecraftRuntime`, which composes the selected
version implementation with `ThreadRuntime`. Root universal packaging tasks combine matching
outputs and own no runtime logic.

For 1.21.11, recipe-display traversal, world-clock access, session detection, Fabric game-test
context access, and Forge `ModList` access are explicit lane bindings. Shared code may call those
bindings but must not inspect a version string or use reflection to choose an API. The Shadow
plugin is build-only and is used solely to isolate remapped Fabric bytecode in the 1.21.11
universal artifact; it is not a runtime dependency.

Root Gradle tasks aggregate all modules and remain the normal contributor interface. See
[Build](BUILD.md) for module-specific commands and outputs.

## Source standards

- Run Gradle on Java 25. The matrix compiles and tests Minecraft 1.21.11 with Java 21 and both
  26.x lanes with Java 25, using the pinned Fabric, NeoForge, and Forge toolchains.
- Do not add Kotlin, `package-info.java`, wildcard imports, or hidden global state.
- Keep Minecraft-version, loader, core, and MCP dependencies inside their documented module
  boundaries.
- Apply both portability rules literally: version-neutral code stays in `:common`; code that uses
  a Minecraft API stays under `minecraft`; identical compile-time-compatible code belongs in
  `minecraft/shared`, while genuine API differences stay in the matching version directory. Code
  that calls a loader API stays in that loader's shared thin adapter source tree.
- Adding a Minecraft version should normally add a matrix entry and a version binding without
  changing `common/src/main`.
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

The root `test` task runs common, all three Minecraft modules, and every version of all three loader
projects. They cover core services, schemas, registries, configuration, integration
activation/isolation, version-specific conversion/support, loader discovery/version handling, and
the real loopback MCP HTTP server without launching Minecraft.
Fake providers should prove core behavior whenever live Minecraft is unnecessary.

Important regression areas include:

- success/error schema validation and deterministic discovery;
- the real serialized built-in `tools/list` catalog: unique stable IDs/order, non-empty
  descriptions, schema defaults/enums/bounds, read-only annotations, session metadata, loader
  neutrality, a wording-insensitive semantic fingerprint, and a 128 KiB payload regression ceiling
  that catches unexpected schema growth without constraining useful catalog content;
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
- common-to-version-to-loader module direction, cross-loader isolation, the absence of Minecraft
  dependencies in common, the absence of Fabric/NeoForge/Forge dependencies in Minecraft source or
  the shared packaged parity fixture, version-lane isolation, and no version literals in shared
  production Java;
- full/unsupported/optional-field version capability states and the exact twenty-one fully
  supported tools declared independently by Minecraft 1.21.11, 26.1.2, and 26.2.

Run focused unit coverage with:

```powershell
.\gradlew.bat test
```

### Development client game test

```powershell
.\gradlew.bat :loaders:fabric:26.2:runClientGameTest
```

This uses the development classpath for interactive diagnosis. It is useful during provider work
but is not the release proof.

### Packaged-client tests

Every version-specific loader project exposes the same normal, restart, and MCP-disabled tasks:

| Project | Dedicated tasks | Universal tasks |
| --- | --- | --- |
| `:loaders:fabric:<version>` | `runProductionClientGameTest`, `runRestartProductionClientGameTest`, `runMcpDisabledProductionClientGameTest` | same names prefixed with `runUniversal` |
| `:loaders:neoforge:<version>` | `verifyNeoForgeProductionClientGameTest`, `verifyNeoForgeRestartProductionClientGameTest`, `verifyNeoForgeMcpDisabledProductionClientGameTest` | same names with `UniversalNeoForge` |
| `:loaders:forge:<version>` | `verifyForgeProductionClientGameTest`, `verifyForgeRestartProductionClientGameTest`, `verifyForgeMcpDisabledProductionClientGameTest` | same names with `UniversalForge` |

Always use a project-qualified task, for example
`.\gradlew.bat :loaders:fabric:26.1.2:runRestartProductionClientGameTest`. Keep each packaged task
in its own Gradle invocation.

Each normal test launches a temporary client with a final, version-specific dedicated or universal
Thread JAR and a separately packaged proof integration/game-test mod. All projects compile the same
loader-neutral parity fixture from `common/src/gametest/java`. It verifies the exact twenty-one-tool catalog, config,
loader identity, menu/world/menu status, MCP initialization and discovery, every tool path, native
recipes and crafting, external integration activation, and controlled gameplay rejection at the
menu.

Launch control remains loader-specific because the APIs are genuinely different: Fabric uses the
Fabric client game-test context, while NeoForge and Forge use bounded event-driven state machines.
Fabric also retains richer deterministic payload assertions for player-state transitions, inventory,
equipment, target blocks, nearby entities, and actual-look target entities with hostile,
non-living, tame-owner, and villager variants; those are provider regression coverage, not a
different loader contract.

These proofs remain graphical client launches because they exercise Minecraft's real client,
integrated server, and OpenGL-backed lifecycle; setting Java's headless flag would not provide the
same coverage. Their test-only harness minimizes local test windows as soon as its client callback
is available. CI leaves the already-invisible Xvfb window active so Minecraft does not throttle
client ticks while an integrated world starts on a resource-constrained runner. Every development,
dedicated, and universal client-test run also seeds an isolated `options.txt` with master audio
muted, fullscreen disabled, and pause-on-focus-loss disabled before Minecraft starts. Normal user
options are never read or changed.

The restart test launches that packaged client again from the same instance and proves an existing
configuration is reloaded, including a changed MCP port. The disabled tests use separate fresh
instances and prove Thread initializes without a listener. None of these tasks
may silently fall back to the source-set development classpath.

## Full local gate

Before committing a release change, run the release-equivalent gate from the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain clean spotlessApply spotlessCheck check build `
  verifyReleaseArtifact releaseBundle verifyReleaseVersion "-PreleaseTag=v<version>"
```

Then run the normal, restart, and MCP-disabled tasks from the table above for dedicated and
universal artifacts in all nine loader projects, one invocation at a time, and finish with
`git diff --check`. CI and the release workflow enumerate the same three-version matrix.

Keep each packaged task in its own Gradle invocation. Loader launch tasks use real client processes
and must not overlap on the shared MCP port or test-instance preparation.

Change the release tag argument when `mod_version` changes. Release versions and tags must use
`MAJOR.MINOR.PATCH` and `vMAJOR.MINOR.PATCH`, respectively. `verifyReleaseArtifact` rejects test
classes, source files, and bundled third-party integrations while checking common contracts,
shared runtime/provider classes, dedicated loader purity, universal loader coverage, and metadata.
`releaseBundle` writes three version-labeled universal JARs and nine dedicated JARs plus SHA-256 files
to `build/release/`. See
[Release](RELEASE.md) for the publishing checklist.

For a release candidate, run the bundle twice from clean outputs and compare all twelve JAR
digests. The tag workflow performs this check automatically before packaged-client proofs.

## Manual MCP smoke test

Use the release JAR, not a development run, for the final human check:

1. Put the matching universal release JAR and loader requirements in a clean Minecraft 1.21.11,
   26.1.2, or 26.2 instance. Repeat this smoke test for all supported versions before release.
2. Launch to the menu and confirm `http://127.0.0.1:25580/mcp` is listening.
3. Connect a real MCP client, complete `initialize` followed by `tools/list`, and confirm twenty-one
   read-only `minecraft.*` tools.
4. Call `minecraft.get_status` in the menu; it must return a controlled unsupported/no-world state.
   Also call `minecraft.get_game_info`, `minecraft.get_client_options`,
   `minecraft.search_items`, and `minecraft.get_capabilities`; confirm they remain available and
   the options response contains the default local-only sections.
5. Load an integrated single-player world and call status, player, world info, inventory, equipment,
   target block, target entity, nearby-entity, nearby-container, container-inspection, unified live item search, vanilla
   advancement list/detail, client options including a bounded keybind request, recipe, crafting,
   registry search, and capability tools.
6. Confirm tools return detached bounded data, recipes reflect the live world, container searches
   skip unloaded chunks, and no call mutates the game. Confirm crafting with omitted or explicit
   `PLAYER_ONLY` scope ignores nearby-only items, while explicit `PLAYER_AND_NEARBY` can use an
   eligible loaded container and reports its source plus any incomplete discovery.
7. Join multiplayer only for rejection verification if appropriate: status and local client options
   remain callable while gameplay tools return `UNSUPPORTED` without exposing live state.
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
