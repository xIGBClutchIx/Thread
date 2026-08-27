# Thread V1 Architecture

## Module shape

```text
:fabric   -- Fabric entrypoint, lifecycle, and integration discovery --\
:neoforge -- NeoForge entrypoint, lifecycle, and integration discovery ---> :common
:forge    -- Forge entrypoint, lifecycle, and integration discovery -----/
:universal -- packaging only: common plus all three adapter outputs

:common
    runtime assembly -> Minecraft providers -> core provider contracts
    MCP transport    -> tool registry      -> core services and DTOs
```

The build produces the recommended `thread-universal-<version>.jar` plus dedicated
`thread-fabric-<version>.jar`, `thread-neoforge-<version>.jar`, and
`thread-forge-<version>.jar` artifacts. `:common` is internal and not installed separately.
`:universal` contains no Java source: it packages intended source-set outputs and metadata directly
without making loader modules depend on one another or merging their release JARs.

Dependencies point from each loader adapter to `:common`. Common production source has no Fabric,
NeoForge, or Forge imports or runtime dependencies. The loader modules do not depend on one another.

## Common ownership

### Core

`common/src/main/java/me/clutchy/thread/core` owns behavior that can be tested without launching
Minecraft:

- immutable DTOs, structured errors, and explicit limits;
- provider contracts for game, player, vanilla advancements, world, recipes, and logical-thread
  execution;
- JSON codecs and schemas;
- tool, context, integration, and extension registries;
- deterministic direct crafting assessment and bounded recursive planning.

Core public contracts use plain Java and Thread types. Raw NBT, component maps, Minecraft objects,
loader types, MCP types, and optional-mod types do not cross this boundary.

### Shared Minecraft edge

`me.clutchy.thread.platform.minecraft` owns code that uses Minecraft classes but no loader API:

- client and integrated-server thread dispatch;
- the single-player session guard and status mapping;
- bounded game, player, vanilla-advancement, world/environment, item, block, entity,
  loaded-container, and live-recipe reads;
- conversion from Minecraft objects to detached Thread DTOs;
- safe Minecraft-facing block/entity extension points and registries.

This code stays shared because it can run unchanged after another client loader invokes it. It does
not wrap every Minecraft class. Providers receive the actual `Minecraft` client at assembly time
and keep Minecraft objects at this outer edge.

Providers read only already-loaded state. They never generate chunks, scan arbitrary world areas,
open hidden containers, resolve unopened loot tables, or mutate Minecraft.

### Shared runtime

`me.clutchy.thread.runtime.ThreadRuntime` performs loader-neutral assembly:

1. load the loader-supplied config path, preserving invalid files while falling back safely;
2. create logical-thread dispatch, session guards, limits, mappers, and extension registries;
3. construct the shared Minecraft providers and composite recipe provider;
4. register the built-in vanilla integration;
5. activate compatible enabled candidates supplied by the loader adapter;
6. start the configured MCP listener and report consistent startup diagnostics;
7. expose the final tool/integration registries and close shared resources when the loader signals
   shutdown.

`ThreadRuntimeInfo` carries the running Thread, Minecraft, and loader identity/version without
depending on a loader API. There is no global service locator or background integration lifecycle.

### MCP and configuration

`me.clutchy.thread.transport.mcp` owns the loopback HTTP listener, protocol validation, discovery,
and JSON-RPC mapping. It invokes only `ToolRegistry` and imports neither Minecraft nor a loader API.
See [MCP notes](MCP_NOTES.md).

`me.clutchy.thread.config` owns the persisted schema and validation. It can disable the listener,
filter tools/integrations, choose an explicit loopback address/port, and tune limits within hard
ceilings. Each loader adapter supplies its config-file location.

## Fabric ownership

`fabric/src/client/java/me/clutchy/thread/platform/fabric` is intentionally small. It owns only:

- `ThreadFabricClient`, the Fabric client entrypoint;
- Fabric Loader lookups for Thread, Minecraft, loader, and installed-mod versions;
- the Fabric config-directory path;
- the Fabric client-stopping event that tells `ThreadRuntime` to close;
- `thread:integrations` entrypoint discovery;
- Fabric version-predicate evaluation for optional integration candidates.

The entrypoint supplies loader metadata, configuration, candidates, and the active mod classloader
to `ThreadRuntime`. It does not contain tool, crafting, MCP, mapping, or live-query behavior.

No generic client-lifecycle or config-directory interface is introduced: those values are consumed
once by each thin entrypoint. Shared providers resolve the current integrated server for each
bounded read, so loader lifecycle differences do not leak into core APIs.

## NeoForge ownership

`neoforge/src/main/java/me/clutchy/thread/platform/neoforge` mirrors the same narrow boundary. It
owns the `@Mod` client bootstrap, `ModList` and Maven-version-range queries, `FMLPaths` config path,
the NeoForge game-shutdown event, and Java `ServiceLoader` candidate-provider discovery. Candidate
providers expose metadata only; compatible implementation classes remain deferred until the shared
integration registry has checked the target mod and version.

NeoForge does not maintain a second integrated-server lifecycle state. The shared providers resolve
Minecraft's current integrated server for each bounded read, which is the same source used on
Fabric and avoids loader-specific state drifting from the game.

## Forge ownership

`forge/src/main/java/me/clutchy/thread/platform/forge` is the equivalent thin Forge boundary. It
owns the `@Mod` client bootstrap, `ModList` and Maven-version-range queries, `FMLPaths` config path,
Forge client setup and shutdown events, and Java `ServiceLoader` candidate-provider discovery.
Common output is compile-only for the adapter and embedded once in the final JAR so ModLauncher does
not see common as a second module.

Forge uses the same shared runtime and Minecraft providers as the other loaders. ForgeGradle launch
metadata and packaged-test wiring stay local to `:forge`; no Forge API leaks into common.

## Loader behavior contract

Every supported loader must hand its metadata, config path, integration candidates, environment,
and mod classloader to the same `ThreadRuntime`. That common path guarantees identical config
fallback, runtime assembly, tool registration, MCP bind handling, diagnostics, and resource close
behavior.

Packaged parity coverage requires each loader to prove:

- the exact same twenty-tool catalog and active vanilla integration;
- correct loader identity plus menu, single-player, and return-to-menu status;
- MCP-enabled, restarted-config, and MCP-disabled startup;
- standard MCP initialization, discovery, tool listing, calls, and controlled menu rejection;
- native live recipes, direct crafting, recursive planning, and external integration discovery;
- centralized single-player safety, with multiplayer rejection covered by the shared session guard;
- clean process exit so the listener can bind again on restart.

The assertions are shared, but launch mechanics remain local: Fabric uses its client game-test API,
while NeoForge and Forge drive their client lifecycles through their own events and screens. Version
parsing, candidate enumeration, config-path lookup, and shutdown event registration also remain
local because those are genuine loader APIs rather than portable behavior.

## Session and threading rules

`minecraft.get_status` is always callable. Other gameplay tools pass through the centralized
shared session guard and reject menus, loading states, missing players, and multiplayer before
exposing game state.

Client-owned reads run on the Minecraft client thread. Integrated-server-owned reads, including
live recipes, advancement progress, world/environment context, block entities, and nearby container
scans, run on the integrated-server thread. Advancement reads first capture the client advancement
tree on the client thread so only entries Minecraft has exposed as visible/known are eligible, then map their
authoritative server-player progress into detached DTOs. Dispatch has a configured deadline;
timeout and lifecycle rejection become structured retryable errors. A request may fail safely if
the world unloads while it is waiting.

Target-entity inspection captures only Minecraft's current client `EntityHitResult`, the exact
entity/player identities, and the dimension on the client thread. The server thread then resolves
that same entity from the authoritative loaded level, rechecks the normal interaction distance
under a fixed six-block ceiling, and maps it through the same bounded `EntityInfo` and entity
enricher path as nearby discovery. It never substitutes a nearby scan or force-loads a chunk when
the target disappears.

## Tools, crafting, and integrations

A `GameTool` owns a stable ID, description, input/output codecs, explicit JSON schemas, read-only
capability metadata, and an execution function. `ToolRegistry` validates input before execution
and validates serialized output before returning it to MCP.

`AdvancementService` applies exact registry-ID or all-term text matching, completion filters,
stable ID ordering, and result limits over a bounded `AdvancementProvider` snapshot. This is a
dedicated vanilla advancement path, not a generic progression or quest API. A future FTB Quests
integration should contribute its own tools unless proven stable shared concepts justify reuse.

`minecraft.get_world_info` is one focused `WorldProvider` snapshot rather than a family of small
environment tools. It resolves the authoritative integrated-server player and already-loaded local
chunk before reading dimension, biome, global spawn, difficulty/hardcore, clocks, weather, light,
moon phase, and native biome climate values. Cross-dimension spawn distance is explicitly absent,
and the provider never loads a chunk or mutates time/weather to complete the snapshot.

`CraftingService` uses maximum-flow allocation so overlapping alternatives cannot spend the same
item twice. `CraftingPlanner` uses one item/surplus ledger, active-path cycle detection,
deterministic local variant scoring, and hard depth/work/quantity limits. Both consume one detached
`CraftingItemSourceProvider` snapshot per invocation. The default snapshot contains only the
36-slot main inventory; an explicit expanded scope adds eligible nearby containers.

Nearby container discovery and inspection remain `WorldProvider` operations. Discovery walks a
distance-ordered, hard-capped set of block positions, skips unloaded chunks, and returns compact
summaries. Individual inspection reuses `BlockInfo`, `BlockEntityInfo`, and the existing
`MinecraftBlockEntityInspectorRegistry`, so contributed inspectors can enrich future custom
machines without a second registry. The crafting source provider reuses the full bounded snapshot
path only for explicit expanded-scope requests and reports truncation or excluded unsafe contents.

`ItemFinder` and `CraftingItemSourceProvider` compose small transport-independent `ItemSource`
snapshots. V1 supplies player inventory, equipment, and nearby-container sources; the nearby source
uses one full bounded container scan on the integrated-server thread. Item search includes offhand
and armor, while crafting intentionally excludes equipment. Main hand is not counted as equipment
because it aliases the selected hotbar slot. Entries and reported source locations are ordered
deterministically. Explicit source composition can accept a future external storage source without
changing the crafting algorithms or making third-party storage part of base Thread.

External packages advertise metadata through the loader-specific catalog (`thread:integrations`
on Fabric and a Java service provider on NeoForge or Forge). Common `IntegrationRegistry` performs enabled,
mod-presence, version, reflective load, and transactional contribution handling. Shared Minecraft extension points use
`MinecraftIntegrationExtensionPoints`; they accept Minecraft inputs on the owning thread and return
detached Thread DTOs.

## Enforced boundaries

Architecture and release tests enforce that:

- common production source imports no Fabric, NeoForge, or Forge API;
- core imports no Minecraft, loader, or MCP API;
- MCP imports no Minecraft or loader API;
- Fabric production code stays inside the Fabric adapter package;
- NeoForge production code stays inside the NeoForge adapter package;
- Forge production code stays inside the Forge adapter package;
- only the supported JDK HTTP server uses `com.sun` APIs;
- optional implementations remain deferred class-name strings;
- each dedicated JAR contains common plus exactly one loader adapter, while the universal JAR
  intentionally contains all three; none contains tests or a bundled third-party adapter;
- universal packaging fails on unexpected duplicate entries, validates consistent metadata, and
  scans compiled common classes for eager loader-specific references;
- no loader module imports or depends on another loader implementation;
- shared packaged parity fixtures import neither loader API;
- each loader's packaged-client tests exercise both its dedicated JAR and the universal JAR through
  menu/world/menu, restart, and MCP-disabled lifecycles.

V1 remains Java-only, Fabric/NeoForge/Forge, read-only, single-player-only, bounded, and
loopback-only.
