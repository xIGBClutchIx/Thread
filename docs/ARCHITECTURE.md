# Thread Architecture

## Module shape

```text
common/                         -> :common
minecraft/
  shared/                       -> shared source sets compiled inside every version lane
  1.21.11/                      -> :minecraft:1.21.11
  26.1.2/                       -> :minecraft:26.1.2
  26.2/                         -> :minecraft:26.2
loaders/
  fabric/{src,1.21.11,26.1.2,26.2}/   -> :loaders:fabric:<version>
  neoforge/{src,1.21.11,26.1.2,26.2}/ -> :loaders:neoforge:<version>
  forge/{src,1.21.11,26.1.2,26.2}/    -> :loaders:forge:<version>
gradle/                          -> version matrix, conventions, packaging, quality, release
```

The build produces independent `thread-<loader>-<minecraft>-<version>.jar` artifacts for Minecraft
1.21.11, 26.1.2, and 26.2. `:common` is internal and not installed separately. Universal JARs are root
packaging tasks driven by `gradle/version-matrix.gradle`; they are not source projects. Each
packages one version's intended source-set outputs and metadata directly without making loader
modules depend on one another or merging their release JARs.

The 1.21.11 universal artifact crosses a namespace boundary that the unobfuscated 26.x lanes do
not have. Fabric consumes the remapped intermediary runtime, while Forge and NeoForge consume the
Mojang-named runtime. The packaging task relocates only the remapped Fabric copy beneath its loader
namespace and retains the ordinary Mojang-named copy for the other loaders. This is deterministic
build-time isolation; the artifact still contains one Minecraft version and performs no runtime
version selection.

There are two independent adaptation axes. Each `:minecraft:<version>` lane depends
only on `:common` and supply one implementation of the focused provider contracts. Each nested
loader/version project consumes common and exactly one Minecraft project, then supplies only
loader-specific startup inputs. Common has no Minecraft or loader dependency, Minecraft sources
have no loader dependency, and no version lane depends on another. Future versions receive another
compile-time lane instead of runtime version branches.

## Common ownership

### Core

`common/src/main/java/me/clutchy/thread/core` owns behavior that can be tested without launching
Minecraft:

- immutable DTOs, structured errors, and explicit limits;
- provider contracts for game, local client options, player, vanilla advancements, world, recipes,
  and logical-thread execution;
- JSON codecs and schemas;
- tool, integration, and extension registries;
- deterministic direct crafting assessment and bounded recursive planning.

Core public contracts use plain Java and Thread types. Raw NBT, component maps, Minecraft objects,
loader types, MCP types, and optional-mod types do not cross this boundary.

## Minecraft-version ownership

The `minecraft` tree owns code that uses Minecraft classes but no loader API:

- client and integrated-server thread dispatch;
- the single-player session guard and status mapping;
- bounded game, local-client-option, player, vanilla-advancement, world/environment, item, block, entity,
  loaded-container, and live-recipe reads;
- conversion from Minecraft objects to detached Thread DTOs;
- safe Minecraft-facing block/entity extension points and registries.

`minecraft/shared` contains sources and tests that compile unchanged against every current version,
including provider assembly and the twenty-one-tool capability set. It is not a separately packaged
module. Each version directory contains only real API/capability bindings: its capability identity,
its multiplayer predicate, and test-only screen access. `MinecraftRuntime` assembles focused
providers and composes them with the version-neutral runtime without a runtime version check,
reflection, one giant adapter, or loader API. Providers receive the actual `Minecraft` client at
this outer edge.

Providers read only already-loaded state. They never generate chunks, scan arbitrary world areas,
open hidden containers, resolve unopened loot tables, or mutate Minecraft.

### Version capabilities

Each Minecraft-version binding supplies a `VersionCapabilities` map keyed by `ToolId`. A tool is
either fully supported, unsupported, or supported with a sorted list of contract-defined optional
fields that the version cannot supply. An absent entry is unsupported. `ThreadRuntime` combines
this support with user configuration before vanilla tools enter the registry, so unsupported tools
never appear in `tools/list`, invocation, or `minecraft.get_capabilities`.

Partial support does not permit invented placeholder data. The relevant Thread DTO/schema must
already define the named field as optional, and the version provider returns that ordinary absence.
Core consumes this support map and focused provider contracts; it never compares Minecraft version
strings. All three current bindings explicitly expose the same twenty-one fully supported tools.
Minecraft 1.21.11 keeps its older recipe-display traversal, world-clock access, session predicate,
and test APIs in small compile-time bindings. The 26.x lanes retain their newer equivalents; no
runtime version test or reflective compatibility path is used.

## Shared runtime

`me.clutchy.thread.runtime.ThreadRuntime` performs loader-neutral assembly:

1. load the loader-supplied config path, preserving invalid files while falling back safely;
2. create the shared extension registry and ask the selected version module for focused providers;
3. combine version support with configured tool filtering and construct the composite recipe provider;
4. register the built-in vanilla integration;
5. activate compatible enabled candidates supplied by the loader adapter;
6. start the configured MCP listener and report consistent startup diagnostics;
7. expose the final tool/integration registries and close shared resources when the loader signals
   shutdown.

`RuntimeProviderFactory` and `RuntimeProviders` are composition contracts, not a gameplay god
object: the bundle retains the existing game, options, advancement, player, world, and recipe
interfaces. `ThreadRuntimeInfo` carries the running Thread, Minecraft, and loader identity/version
without depending on Minecraft or a loader API. There is no global service locator or background
integration lifecycle.

### MCP and configuration

`me.clutchy.thread.transport.mcp` owns the loopback HTTP listener, protocol validation, discovery,
and JSON-RPC mapping. It invokes only `ToolRegistry` and imports neither Minecraft nor a loader API.
See [MCP notes](MCP_NOTES.md).

`me.clutchy.thread.config` owns the persisted schema and validation. It can disable the listener,
filter tools/integrations, choose an explicit loopback address/port, and tune limits within hard
ceilings. Each loader adapter supplies its config-file location.

## Fabric ownership

`loaders/fabric/src/client/java/me/clutchy/thread/platform/fabric` is intentionally small. It owns
only:

- `ThreadFabricClient`, the Fabric client entrypoint;
- Fabric Loader lookups for Thread, Minecraft, loader, and installed-mod versions;
- the Fabric config-directory path;
- the Fabric client-stopping event that tells `ThreadRuntime` to close;
- `thread:integrations` entrypoint discovery;
- Fabric version-predicate evaluation for optional integration candidates.

The entrypoint supplies loader metadata, configuration, candidates, and the active mod classloader
to the selected lane's compiled `MinecraftRuntime`, which composes the version implementation with
`ThreadRuntime`. It does
not contain tool, crafting, MCP, mapping, or live-query behavior and imports no `net.minecraft`
classes.

No generic client-lifecycle or config-directory interface is introduced: those values are consumed
once by each thin entrypoint. Shared providers resolve the current integrated server for each
bounded read, so loader lifecycle differences do not leak into core APIs.

## NeoForge ownership

`loaders/neoforge/src/main/java/me/clutchy/thread/platform/neoforge` mirrors the same narrow
boundary. It owns the `@Mod` client bootstrap, `ModList` and Maven-version-range queries, `FMLPaths` config path,
the NeoForge game-shutdown event, and Java `ServiceLoader` candidate-provider discovery. Candidate
providers expose metadata only; compatible implementation classes remain deferred until the shared
integration registry has checked the target mod and version.

NeoForge does not maintain a second integrated-server lifecycle state. The shared providers resolve
Minecraft's current integrated server for each bounded read, which is the same source used on
Fabric and avoids loader-specific state drifting from the game.

## Forge ownership

`loaders/forge/src/main/java/me/clutchy/thread/platform/forge` is the equivalent thin Forge boundary. It
owns the `@Mod` client bootstrap, `ModList` and Maven-version-range queries, `FMLPaths` config path,
Forge client setup and shutdown events, and Java `ServiceLoader` candidate-provider discovery.
Common and the selected Minecraft-version output are compile-only for the adapter and embedded once in the final
JAR so ModLauncher does not see either internal module as a second mod.

Forge uses the same shared runtime and selected-version providers as the other loaders. ForgeGradle
launch metadata and packaged-test wiring stay local to its project; no Forge API leaks into common
or a version module.

## Loader behavior contract

Every supported loader must hand its metadata, config path, integration candidates, environment,
and mod classloader to the same selected version runtime. The lane-compiled `MinecraftRuntime.start`
then enters the same `ThreadRuntime` path. This guarantees
identical config fallback, provider assembly, capability filtering, tool registration, MCP bind
handling, diagnostics, and resource close behavior.

Packaged parity coverage requires each loader to prove:

- the exact same twenty-one-tool catalog and active vanilla integration;
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

`minecraft.get_status`, `minecraft.get_game_info`, `minecraft.get_client_options`,
`minecraft.search_items`, and `minecraft.get_capabilities` are always callable. Client options use
only local `Minecraft.options` state, while item search reads the local item registry; neither
consults a world, player, or server. Other gameplay tools pass through the centralized shared
session guard and reject menus, loading states, missing players, and multiplayer before exposing
game state.

Client-owned reads run on the Minecraft client thread. Integrated-server-owned reads, including
player status, live recipes, advancement progress, world/environment context, block entities, and
nearby container scans, run on the integrated-server thread. Advancement reads first capture the
client advancement tree on the client thread so only entries Minecraft has exposed as visible/known
are eligible, then map their authoritative server-player progress into detached DTOs. Dispatch has a configured deadline;
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

`minecraft.get_client_options` maps native client options into stable sectioned Thread DTOs on the
client thread. The default response omits keybinds; callers must select `KEYBINDS`, whose page size
and per-binding conflict list have hard limits. Section order, sound-category order, keybind order,
and conflict order are deterministic. This client-only provider deliberately bypasses the gameplay
session guard so menu and multiplayer calls cannot accidentally expose gameplay state.

`AdvancementService` applies exact registry-ID or all-term text matching, completion filters,
stable ID ordering, and result limits over a bounded `AdvancementProvider` snapshot. This is a
dedicated vanilla advancement path, not a generic progression or quest API. A future FTB Quests
integration should contribute its own tools unless proven stable shared concepts justify reuse.

`minecraft.get_world_info` is one focused `WorldProvider` snapshot rather than a family of small
environment tools. It resolves the authoritative integrated-server player and already-loaded local
chunk before reading dimension, biome, global spawn, difficulty/hardcore, clocks, weather, light,
moon phase, and native biome climate values. Cross-dimension spawn distance is explicitly absent,
and the provider never loads a chunk or mutates time/weather to complete the snapshot.

`minecraft.get_player` similarly resolves the authoritative integrated-server player before mapping
one bounded `PlayerStatus`. It reuses `StatusEffectInfo` and compact world model types, groups related
armor, air, movement, and condition values, and avoids raw attributes, NBT, identity, or full vehicle
entity dumps. Position/dimension and hardcore intentionally overlap world context, while detailed
equipment stays in `minecraft.get_equipment`.

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
snapshots. Thread supplies player inventory, equipment, and nearby-container sources; the nearby source
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

- common production source imports no Minecraft, Fabric, NeoForge, or Forge API and has no
  Minecraft build dependency;
- neither shared nor version-specific Minecraft source imports Fabric, NeoForge, or Forge API;
- version lanes do not reference one another, and shared production Java contains no supported
  Minecraft-version literal or runtime version branch;
- core imports no Minecraft, loader, or MCP API;
- MCP imports no Minecraft or loader API;
- loader production code stays inside its adapter package, imports no `net.minecraft` gameplay
  classes, and does not reach into Minecraft provider/service implementations;
- only the supported JDK HTTP server uses `com.sun` APIs;
- optional implementations remain deferred class-name strings;
- each dedicated JAR contains common, exactly one Minecraft implementation, and exactly one loader
  adapter, while each universal JAR contains one Minecraft version and all three matching loaders;
  mapped lanes may contain a relocated Fabric namespace copy but never cross-version classes,
  tests, or a bundled third-party adapter;
- matrix-driven universal packaging has no per-version source project, fails on unexpected
  duplicate entries, validates consistent metadata, and scans compiled shared classes for eager
  loader-specific references;
- no loader module imports or depends on another loader implementation;
- shared packaged parity fixtures import neither loader API;
- each version/loader project's packaged-client tests exercise its dedicated JAR and matching
  universal JAR through menu/world/menu, restart, and MCP-disabled lifecycles.

Thread remains Java-only, Fabric/NeoForge/Forge, read-only, single-player-only, bounded, and
loopback-only.
