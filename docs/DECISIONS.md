# Thread 1.0 Decisions

This file records current architectural choices that are easy to accidentally undo. Historical
implementation steps belong in Git, not here.

## Thread is a context layer

Thread exposes structured live Minecraft facts to external clients. It does not embed an AI model,
provider SDK, autonomous player, or custom assistant UI. MCP is the first transport over core tools,
not the core API.

## 1.0 is deliberately narrow

Thread 1.0 is a Java client mod for Fabric, NeoForge, and Forge on Minecraft 1.21.11, 26.1.2, and 26.2.
The 1.21.11 lane targets Java 21; 26.x targets Java 25. Every lane remains single-player,
read-only, bounded, and loopback-only.
Gameplay tools reject multiplayer before exposing state. `minecraft.get_status`,
`minecraft.get_game_info`, and the local-only `minecraft.get_client_options` remain available from
every client state.

Thread does not implement remote access, authentication, actions, or dedicated-server behavior.
Minecraft versions are separate compile-time lanes; no runtime version selection or cross-version
JAR is supported.

## Minecraft-version and loader adapters are separate axes

`:common` owns version-neutral core, configuration, MCP, runtime/tool orchestration, focused
provider contracts, DTOs, crafting, item search, serialization, and integrations. It has no
`net.minecraft` dependency. `minecraft/shared` owns Minecraft-facing code that compiles unchanged
inside every supported lane; it is not a runtime artifact. Genuine API and
capability identity differences remain in the matching version directory. Each version project
depends on common but no loader. The Fabric, NeoForge, and Forge source trees under `loaders/` own
only their entrypoint, Loader API, lifecycle, config path, version predicate, and
integration-discovery wiring; nested Gradle projects compile them against one version lane.

All entrypoints resolve those loader-specific values and call the lane-compiled `MinecraftRuntime`.
Shared Minecraft assembly enters `ThreadRuntime` through a small
provider-factory contract. Configuration fallback, capability filtering, reflective
integration activation, MCP bind handling, startup diagnostics, and resource close behavior are one
shared lifecycle rather than parallel loader implementations.

Universal JARs are root packaging tasks, not per-version source projects. Each combines common,
exactly one Minecraft implementation, and the three matching loader outputs from the central build
matrix. Dedicated JARs remain canonical and independently installable. Unexpected duplicate entries
fail the build; the one known loader-branded `pack.mcmeta` collision is replaced by a neutral
universal descriptor.

Minecraft 1.21.11's production Fabric artifact uses intermediary names after remapping, while its
Forge and NeoForge artifacts use Mojang names. Its universal packaging task therefore relocates
only the remapped Fabric runtime copy beneath the Fabric namespace and retains the Mojang-named
copy for the other loaders. This build-time namespace isolation is source-less, deterministic, and
limited to one Minecraft version. It is not a runtime loader/version selector. The Shadow plugin
exists only on the build classpath for this transformation.

Core DTOs, providers, registries, and services contain no Minecraft, loader, MCP, raw NBT, generic
component map, or optional-mod type. Each Minecraft adapter converts live game objects into
detached DTOs. MCP maps only the tool registry. Common has neither Minecraft nor loader imports or
runtime dependencies.

Thread does not use Architectury or a custom platform/version god object, and it does not wrap every
Minecraft class. `RuntimeProviders` is only a composition bundle over the existing focused
interfaces. Config-directory and lifecycle abstractions remain absent because thin loader
entrypoints resolve those values once. Adding a Minecraft version should normally require a matrix
entry and version binding, not a change to `common/src/main`. Future versions receive separate
compile-time projects rather than runtime `if (minecraftVersion)` branches.

Each version supplies `VersionCapabilities` keyed by stable tool ID. Support is fully supported,
unsupported, or supported with explicitly named optional fields unavailable. Missing map entries
are unsupported and never enter discovery or capabilities. Partial support is legal only when the
existing Thread contract marks those fields optional; providers return ordinary absence rather
than fabricated values. Core consumes this map and provider contracts without comparing version
strings. Minecraft 1.21.11, 26.1.2, and 26.2 each explicitly declare all twenty-one tools fully
supported.

Java type documentation belongs on public types and architecture documentation belongs under
`docs/`; `package-info.java` is not used.

## Versions and dependencies are pinned

The 1.0 baselines are Minecraft 1.21.11 on Java 21 with Fabric API 0.141.6+1.21.11, NeoForge
21.11.45, and Forge 61.2.1; Minecraft 26.1.2 on Java 25 with Fabric API 0.154.0+26.1.2,
NeoForge 26.1.2.41-beta, and Forge 64.0.12; and Minecraft 26.2 on Java 25 with Fabric API
0.154.0+26.2, NeoForge 26.2.0.62, and Forge 65.1.2. All use Fabric Loader 0.19.3. Shared build
pins are Loom 1.17.19, ModDevGradle 2.0.144, ForgeGradle 7.0.35, Shadow 9.6.1, Gradle 9.5.1, Spotless 8.10.0,
google-java-format 1.36.0, Checkstyle 14.0.0, and JUnit 6.1.2.

`gradle/version-matrix.gradle` is the single source of truth for Java, Minecraft, Fabric
Loader/API, NeoForge, Forge, and supported loader-combination metadata. Settings, module
conventions, universal packaging, release validation, and release bundling consume that ordered
matrix instead of maintaining parallel version constants.

Minecraft 1.21.11 is the current minimum supported version. Minecraft 1.21.1 and older are deferred
because crossing that compatibility gap requires substantial version-specific Minecraft, mappings,
and loader work followed by the same dedicated and universal packaged parity proof as every current
lane. Older-version support may be reconsidered as a separate roadmap effort, but it is not a 1.0
priority and must not be introduced as an incidental compatibility branch in shared code.

The pre-1.0 context registry was removed because no runtime or MCP path exposed registered contexts.
Integrations retain the contribution paths that have real consumers: tools, recipes, typed
Minecraft-edge enrichers/inspectors, and bounded capability metadata.

Minecraft 1.21.11 and 26.1.2 expose the multiplayer predicate differently from 26.2. Their small
session bindings use the public inverse `!Minecraft.isSingleplayer()` after confirming a loaded
world, preserving rejection of LAN-published integrated sessions without reflection or runtime
version branching. The older lane also owns its recipe-display traversal and world-clock access;
the matching 26.x implementations stay in their lanes. Test-only screen, Fabric context, and Forge
`ModList` access follow the same compile-time rule. Tool contracts and capability states are equal.

Minecraft supplies Gson in every supported lane. Thread uses that game-provided runtime behind
explicit Thread-owned JSON schemas rather than bundling another JSON library or generating schemas
through reflection.

## Live native state is authoritative

Providers pull bounded snapshots on the correct Minecraft logical thread. World queries inspect
only already-loaded state and never force-load chunks.

Recipes come from the integrated server's final live `RecipeManager`, including active datapack and
installed-mod changes. Thread has no static vanilla recipe catalog.

## Client options are local, stable, and bounded

`minecraft.get_client_options` reads `Minecraft.options` on the client thread and deliberately does
not use the single-player gameplay guard. It remains available in menus and multiplayer because it
does not touch a world, player, connection, or server. All values are copied into stable sectioned
Thread DTOs; native `Options`, `OptionInstance`, `KeyMapping`, and component objects never cross the
provider boundary.

The default response includes general, video, audio, controls, accessibility, and chat sections but
excludes keybinds. Keybinds are opt-in, limited to 64 by default and 128 maximum, and carry explicit
total/returned/truncation metadata. Per-binding conflicts are capped at 16. Sections, audio
categories, keybinds, and conflicts have deterministic ordering so repeated reads serialize
consistently. The tool is read-only and provides no setting-mutation contract.

Vanilla advancement identity is gated by the client advancement tree, so Thread never broadens a
query to entries Minecraft has not exposed as visible/known to the player. Progress is then read
from the matching integrated-server player and returned as detached criteria, requirement counts,
hierarchy, display metadata, and timestamps. The provider is advancement-specific: Thread does not
define a generic progression or quest framework, and base Thread does not claim FTB Quests support.

## World context is one authoritative snapshot

`minecraft.get_world_info` stays one focused `WorldProvider` operation rather than splitting biome,
time, weather, light, and spawn into overlapping tools. The client thread performs the centralized
single-player guard and captures the integrated server plus local player identity; the server
thread then resolves that player and reads one already-loaded local chunk.

The snapshot uses canonical dimension/biome IDs, the active language's biome translation when one
exists, the global respawn data, level difficulty/hardcore and game time, the overworld clock,
native weather/light/moon attributes, and biome base temperature/precipitation capability. Spawn
distance is straight-line block distance to the spawn block center only when both positions share a
dimension; it is `null` across dimensions. Neither current Minecraft adapter exposes biome downfall
through a clean public API, so Thread does not reflect into private climate data or invent a
downfall value.
The tool never loads chunks, predicts weather, or changes time/weather/world state.

## Player context is focused and server-authoritative

`minecraft.get_player` captures the integrated server plus local player identity behind the shared
session guard, then resolves the matching `ServerPlayer` and maps one detached snapshot on the
server thread. This avoids mixing delayed client mirrors with authoritative vitals, effects,
movement, vehicle, respawn, game-mode, and hardcore state.

The response groups armor, air, movement, and conditions rather than exposing raw attributes or
internal player data. Active effects reuse `StatusEffectInfo`, sort by registry ID, and stop at 64.
Vehicle data is only a compact type/name summary, and player-specific respawn is explicitly null when
Minecraft will fall back to world spawn. Position/dimension and hardcore intentionally overlap
`minecraft.get_world_info` to keep both snapshots usable independently; full equipment stacks remain
exclusive to `minecraft.get_equipment`.

## Target entities reuse the bounded entity contract

`minecraft.get_target_entity` accepts no search arguments and inspects only Minecraft's current
client `EntityHitResult`. The client thread captures the exact entity/player identities and
dimension; the integrated-server thread resolves that same entity from already-loaded state and
rechecks normal interaction reach under a fixed six-block ceiling. Thread does not replace a lost
target with a nearby scan.

Both target and nearby tools reuse `EntityInfo` and the existing entity enricher registry. The
shared shape now includes non-empty equipment slots, at most 64 stable-ID-ordered active effects,
reliable vanilla age, tame/loaded-owner name, and villager profession/level metadata. Unsupported
fields stay null or empty, owner UUIDs and raw NBT/components remain absent, and all reads are
single-player-only and non-mutating.

## Nearby containers are bounded context and explicit crafting input

`minecraft.get_nearby_containers` scans only already-loaded chunks within a hard 16-block ceiling
and returns at most 64 distance-ordered summaries. Each summary includes at most four occupied
slots. `minecraft.inspect_container` accepts one position within the same range and reuses the
existing safe block/block-entity DTOs and inspector registry for full visible contents and selected
machine state. `minecraft.find_item` reuses one full bounded scan to aggregate matching player and
container stacks with structured source locations; it does not inspect containers one by one.

Unopened loot containers remain unresolved because reading their slots would mutate world state.
The tools run on the integrated-server thread and never force-load chunks. Crafting defaults to the
player's 36-slot main inventory; only explicit `PLAYER_AND_NEARBY` requests add eligible containers
through the same full bounded snapshot path. Truncation and skipped unresolved/content-limited
containers make the source status incomplete instead of silently overstating certainty.

The transport-independent `ItemSource` boundary is shared by live search and scoped crafting.
`CraftingItemSourceProvider` accepts explicit source composition so future integration wiring can
add storage without rewriting the allocation/planning algorithms, but base Thread registers no
third-party storage source and exposes no public storage contribution point yet.

## Crafting intelligence remains deterministic and bounded

Direct craftability uses maximum-flow allocation so overlapping alternatives cannot spend the same
item unit twice. Recursive planning uses one shared item/crafted-surplus ledger, active-path cycle
detection, deterministic local variant scoring, and hard depth/work/quantity limits. Every call
captures one detached source snapshot before recipe assessment. Live allocations preserve player
slot or container provenance; planned intermediate output has no live source location.

This complexity is retained because greedy allocation, global visited sets, or unbounded recursion
produce incorrect or unsafe results. Equipment is not eligible crafting supply. Planning does not
model stations/fuel, globally optimize all combinations, move items, or craft items.

## Optional integrations load metadata first

Third-party adapters ship as separate Thread Integrations JARs. Their Fabric entrypoint or NeoForge/Forge
Java service returns only `IntegrationCandidate` metadata. Thread applies enabled, mod-presence, and version checks before
`ReflectiveIntegrationLoader` resolves the implementation class name.

Reflection is retained specifically to prevent absent optional APIs from linking early. NeoForge and Forge
uses `ServiceLoader` only for metadata providers; eager implementation imports and class literals
would break that guarantee.

Registration is transactional and contribution failures are isolated. Base Thread registers only
the built-in vanilla integration.

## Native recipes remain the guarded fallback

The composite recipe provider captures the native result first so session rejection and query
limits cannot be bypassed. The first successful non-empty external provider in stable integration-ID
order may replace that result for one item. Empty or failed contributions preserve the native
result unchanged.

## MCP supports both current client openings without sessions

Thread's JDK HTTP adapter supports standard `initialize`/`notifications/initialized` clients and
MCP `2026-07-28` stateless `server/discover` clients on the same POST endpoint. Both use the same
`tools/list` and `tools/call` implementation.

Initialization is compatibility negotiation, not stored session state. Thread neither emits nor
requires `Mcp-Session-Id`, custom mirrored method/name headers, or per-request protocol metadata.
Legacy GET/SSE and non-tool MCP feature surfaces are absent.

## The MCP catalog is a model-facing contract

Thread assumes an unfamiliar client may see only initialization and `tools/list`. Built-in tool
descriptions therefore state the selection boundary against their closest alternatives and append
one consistent availability sentence. Input schemas, rather than a Thread-specific prompt, carry
exact-ID matching, units, enums, optional defaults, hard ceilings, and the warning that configured
limits may be lower. Tool IDs and existing behavior remain unchanged.

Catalog validation uses the actual loopback `tools/list` response. It checks ordered unique IDs,
schema structure and metadata, read-only/non-destructive/idempotent/closed-domain annotations,
session availability, loader neutrality, and a 128 KiB serialized-response regression ceiling. The
ceiling catches unexpected schema growth; it is not a target for constraining useful descriptions,
schemas, or features. A semantic SHA-256 snapshot excludes tool wording and JSON Schema
`description` annotations but retains names, schemas, defaults, limits, annotations, availability,
and order. This catches meaningful contract drift without turning normal copy editing into snapshot
maintenance.

`openWorldHint` remains false because Thread's domain is one bounded local Minecraft client; it
does not communicate with an unbounded external world. Dynamic game state is still guarded and
validated at call time.

## Quality and release proofs are part of 1.0

Spotless, Checkstyle, compiler checks, unit/architecture tests, release-artifact inspection, and
three packaged-client runs per artifact/loader pairing are mandatory. Dedicated and universal
normal tests use the final runtime JAR in
a real temporary single-player world; restart proves persisted configuration reload, and disabled
startup proves tools initialize without MCP. All three loader proof mods compile one shared parity
fixture for the exact catalog, config, lifecycle, MCP, recipes/crafting, and integration contract;
only their launch mechanics remain separate. Artifact inspection keeps dedicated JARs loader-pure,
requires common, exactly one Minecraft implementation, and matching loader adapters in each
universal JAR, rejects cross-version classes and duplicates,
and scans compiled shared classes for eager loader-specific references. A release is not validated
by compilation alone.
