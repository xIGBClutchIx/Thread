# Thread Architecture Decisions

This is a lightweight decision log. Update decisions when implementation evidence changes them.

## D001: Thread is a context/tool layer, not an AI model

**Status:** Accepted

Thread exposes structured Minecraft information. AI clients are consumers.

Why:

- avoids provider lock-in
- supports Codex/ChatGPT/other MCP clients
- lets future in-game UI reuse the same functionality
- keeps secrets/API keys out of V1

## D002: MCP is the first transport, not the core API

**Status:** Accepted

All MCP-specific types and lifecycle code live behind a transport adapter.

Why:

- MCP will evolve
- SDK support can lag the protocol
- future HTTP/WebSocket/in-game adapters should reuse the same tools

## D003: V1 is read-only

**Status:** Accepted

No tool mutates player/world/server state.

Why:

- much smaller security surface
- useful product can be proven without actions
- avoids confirmation/griefing/cheat semantics in V1

## D004: Fabric only for V1

**Status:** Accepted

Design boundaries for future loader support, but do not build NeoForge simultaneously.

Why:

- lowers implementation/test matrix
- abstractions can be validated before duplicating platform code

## D005: Core DTOs are Minecraft-type-free

**Status:** Accepted

Platform providers convert Minecraft objects to Thread models before crossing the boundary.

Why:

- unit testing
- transport independence
- loader portability
- stable serialization contracts

## D006: Live state is pulled through tools

**Status:** Accepted

Do not continuously stuff inventory/world state into prompt/context resources.

Why:

- lower token usage
- fresher data
- explicit access
- easier privacy/security reasoning

## D007: V1 world queries are bounded and do not load chunks

**Status:** Accepted

Why:

- prevents accidental performance problems
- avoids turning an AI query into world generation/scanning
- makes behavior predictable on multiplayer servers

## D008: Loopback-only is the V1 default

**Status:** Accepted

Prefer making public/remote exposure impossible or clearly opt-in until a real authentication model exists.

## D009: V1 targets current MCP through a narrow transport adapter

**Status:** Accepted

Validated on 2026-08-24:

- the current MCP specification is `2026-07-28` and uses a stateless request/response core;
- Codex's Streamable HTTP client initializes servers and consumes server instructions from the
  initialization response;
- the MCP versioning specification permits a server to expose stateless and initialization-based
  flows on the same endpoint;
- the official Java SDK is still unnecessary for Thread's narrow tools-only surface.

Thread implements a narrow dual-flow tools surface inside `transport.mcp`: `initialize`,
`notifications/initialized`, `server/discover`, `tools/list`, and `tools/call`, plus standard
protocol-version validation and structured error mapping. Initialization is transport compatibility,
not a stateful Thread session: no session ID is minted or required. Legacy HTTP+SSE is not
implemented.

The adapter uses the JDK HTTP server, binds only to loopback, and has no MCP SDK dependency. A
stable Java SDK release with verified `2026-07-28` interoperability may replace it later without
changing core APIs.

## D010: V1 targets one pinned Minecraft version

**Status:** Accepted

Select the exact stable version during Slice 0.

Do not create a generic version abstraction until a second supported version proves what actually varies.

## D011: V1 is implemented in Java

**Status:** Accepted

Thread V1 uses Java rather than Kotlin.

Why:

- keeps the Fabric/JVM implementation conventional and dependency-light
- avoids requiring Kotlin runtime/tooling for the mod
- matches the desired implementation language for the project

The architecture remains language-agnostic at the protocol and DTO level.

## D012: Session status is a dedicated tool

**Status:** Accepted

`minecraft.get_status` is separate from `minecraft.get_game_info`.

Why:

- status must work from the main menu when no player/world exists
- clients need a cheap preflight before invoking gameplay tools
- game/version metadata changes rarely, while session state changes frequently
- keeps multiplayer rejection and world lifecycle state explicit

`minecraft.get_status` is allowed in all client states. Other gameplay tools require a supported single-player session.

## D013: Do not use `package-info.java`

**Status:** Accepted

Thread V1 does not use `package-info.java` files.

Why:

- avoids boilerplate files that add little value to this project
- keeps package/project documentation centralized in `docs/`
- type-specific API documentation belongs on the relevant public class or interface

Codex should not add `package-info.java` for Javadocs, annotations, or style conventions unless this decision is explicitly revisited.

## D014: Formatting and linting are enforced from Slice 0

**Status:** Accepted

Thread V1 uses Spotless as the formatting entry point and Checkstyle as the Java lint/style checker. The concrete formatter version is pinned during Slice 0; prefer `google-java-format` through Spotless unless the selected Java/Minecraft toolchain exposes a compatibility issue.

Why:

- keeps human- and agent-authored code consistent
- removes formatting debates from reviews
- makes quality failures reproducible locally and in CI
- keeps the ruleset small enough to avoid fighting Fabric/Minecraft patterns

`./gradlew build` must include the required quality gates. CI checks formatting but does not rewrite source.

## D015: Public contracts are documented without comment noise

**Status:** Accepted

Public Thread APIs and extension points should have useful Javadocs. Implementation comments should document non-obvious reasoning such as threading, logical-side ownership, lifecycle races, protocol workarounds, or safety bounds.

Thread does not require boilerplate Javadocs/comments on every trivial accessor, override, or private helper. `package-info.java` remains prohibited.

## D016: GitHub Actions is part of the V1 engineering baseline

**Status:** Accepted

Slice 0 establishes CI for pull requests, primary-branch pushes, and manual runs. The workflow executes the same Gradle formatting/lint/test/build path used locally. Slice 6 adds a tag-driven validated release build that produces the installable JAR artifact.

Automatic publishing to Modrinth, CurseForge, Maven repositories, or other distribution services is deferred until distribution requirements are intentionally designed.

## D017: V1 foundation versions are pinned

**Status:** Accepted

Slice 0 pins this baseline:

- Minecraft `26.2`
- Java `25`
- Fabric Loader `0.19.3`
- Fabric API `0.154.0+26.2`
- Fabric Loom `1.17.19`
- Gradle `9.5.1`
- Spotless `8.10.0`
- google-java-format `1.36.0`
- Checkstyle `14.0.0`
- JUnit `6.1.2`
- Gson `2.14.0` (provided by Minecraft 26.2)

Minecraft 26.2 is the current stable Fabric target at the time of implementation, and the Fabric
example project uses Java 25 and Gradle 9.5.1. Stable Loom 1.17.19 is pinned instead of the example
project's moving `1.17-SNAPSHOT` coordinate.

V1 intentionally supports only this Minecraft target. Dependency automation may propose updates,
but Minecraft, Fabric, Java, Gradle, and MCP changes require deliberate compatibility validation.

## D018: Core JSON contracts use explicit schemas with Minecraft-provided Gson

**Status:** Accepted

Slice 1 validates explicit Thread-owned JSON Schema documents and uses Gson for Java/JSON
conversion. Schemas are not generated from Java reflection, and serialized outputs are checked
against the same declared contracts before crossing the registry boundary.

Minecraft 26.2 supplies Gson 2.14.0 on Thread's compile, test, and client runtime classpaths, so V1
does not add or bundle a second JSON implementation. Gson remains a serialization detail: game
DTOs and provider interfaces do not expose Gson, Minecraft, Fabric, or MCP types.

Why:

- one explicit schema remains the discovery and validation contract;
- output validation catches DTO/schema drift before transport serialization;
- using the runtime-provided library keeps the mod dependency surface small;
- the MCP adapter can translate JSON without leaking protocol types into core abstractions.

## D019: V1 configuration cannot weaken local security boundaries

**Status:** Accepted

Slice 5 persists a versioned `config/thread.json` using Minecraft-provided Gson. Configuration may
disable the listener, choose an explicit loopback host and port, filter registered tools, and lower
or raise bounded query/transport limits within fixed hard ceilings.

Non-loopback hosts remain invalid rather than becoming an expert-mode opt-in. Disabled tools are
filtered before registry insertion so discovery cannot advertise an unusable or forbidden tool.
Invalid files are preserved and the launch falls back to safe defaults without logging the file
contents. The MCP request-concurrency limit and game-thread deadline are also server-enforced;
clients cannot override them per call.

## D020: Rich live context uses shared bounded snapshots

**Status:** Accepted

Inventory, equipment, recipe results, and inspected container contents share one explicit
`ItemStackInfo` contract. It separates localized base and custom names, includes durability and
canonical enchantments, and whitelists a bounded set of component values. Equipment contains all
six positions with null items for empties; the main inventory omits empty positions
and excludes equipment to avoid duplication.

Nearby entities include detached identity, names, position, distance, and living health. Behavior
Fabric assigns nullable classifications from reliable Minecraft type markers and uses no
heuristics. Target-block inspection captures the normal client raycast but reads block state and
block entities from already-loaded integrated-server state. Vanilla container inspection is capped,
does not serialize raw NBT/components or resolve unopened loot tables.

Fabric owns a small ordered block-entity inspector registry. Future mod integrations may register a
higher-priority inspector without changing core models, tool handlers, or MCP transport. No
third-party integration ships with this extension point.

## D021: Craftability is a core service with deterministic allocation

**Status:** Accepted

`CraftingService` consumes the existing `PlayerProvider` and `RecipeProvider` contracts. The
`minecraft.can_craft` and `minecraft.get_missing_ingredients` tools delegate to it and share one
structured assessment contract; MCP contains no crafting comparison logic.

Ingredient `itemIds` are the complete alternatives resolved by the recipe adapter, while `tagIds`
retain source-tag provenance. For each recipe variant, a deterministic maximum-flow allocation
assigns inventory units to ingredient groups. This maximizes satisfied requirements without
allowing overlapping alternatives to claim the same units. Constrained groups sort before broader
groups for stable explanations, identical groups merge defensively, and recipe IDs are not treated
as unique; stable one-based variant ordinals identify returned entries.

The assessment covers one execution of each represented recipe using the current 36-slot main
inventory. It deliberately excludes equipment, nearby storage, recursive ingredient crafting,
workstation/fuel feasibility, third-party recipe integrations, and crafting actions.

Why:

- the logic is reusable by future transports and enriched recipe providers;
- explicit allocations make `available` and `missing` counts auditable;
- maximum flow handles overlapping alternatives correctly where greedy sums cannot;
- the narrow read-only scope preserves V1 session and safety boundaries.

## D022: Recursive plans use active paths and deterministic local selection

**Status:** Accepted

`CraftingPlanner` consumes the same detached player/recipe providers as `CraftingService` and reuses
the service's canonical recipe ordering and maximum-flow allocation for scaled recipe executions.
The `minecraft.get_crafting_plan` handler only delegates to that core service; MCP transport remains
unchanged and contains no recipe logic.

The selected plan has one shared inventory-and-crafted-surplus ledger. Candidate state is isolated
while comparing variants, then only the chosen state is committed. This prevents cross-branch
double-counting without incorrectly memoizing an inventory-dependent answer.

Cycle detection tracks the current item path. Direct, indirect, and tag/alternative cycles stop with
a structured `CYCLE` issue and path; a global visited set is deliberately not used because an item
may be valid in multiple independent branches. Maximum depth and total step/branch/quantity limits
also stop pathological acyclic or broad graphs with `MAX_DEPTH` or `PLAN_LIMIT` issues.

Variants and alternatives are chosen locally by safety issues, unresolved amount, raw shortages,
step count, and canonical order. This prefers a non-cyclic branch and produces repeatable output
without exponential global optimization. Recipe definitions alone may be cached. Planning remains
read-only and excludes automatic crafting, nearby storage, station/fuel feasibility, and third-party
recipe integrations.

## D023: Optional integrations use metadata-first discovery and transactional contributions

**Status:** Accepted

Optional integrations are declared by stable ID, target mod ID, version requirement, and
implementation class-name string. Thread evaluates enabled configuration, Fabric Loader mod
presence, and version compatibility before the class loader sees the implementation name. The
catalog does not import optional implementations, use class literals, or use eager `ServiceLoader`
discovery.

`ThreadIntegration.register` receives one transactional `IntegrationContext`. It may contribute
read-only tools, bounded contexts, preferred recipe definitions, typed platform extensions, and
bounded capability metadata. Successful callbacks commit as one startup unit; callback, duplicate,
metadata, linkage, or construction failures leave no partial registration and do not stop later
optional candidates.

Core owns the generic typed extension registry. Fabric owns extension contracts that require
Minecraft types: block-entity inspection, target-block enrichment, and nearby-entity enrichment.
Those contributors run on the provider's existing logical-thread path and return detached Thread
DTOs. Optional recipe/enrichment failures are isolated at invocation time so vanilla results and
central session guards remain authoritative.

The lifecycle stays deliberately small. Integrations have a registration callback only; no generic
start, stop, reload, event bus, or background-task system exists until an implemented integration
proves one is required. This slice ships a test-only proof integration and no substantial
third-party mod integration.

## D024: The bundled JEI proof is retired

**Status:** Superseded by D026

Slice 10 temporarily proved the generic recipe-provider extension against JEI. Slice 12 removes
that adapter, its API/runtime dependencies, plugin entrypoints, capability metadata, test fixtures,
packaged runs, CI/release paths, and installation support from base Thread.

The experiment established that existing recipe and crafting tools can consume contributed
`IntegrationRecipeProvider` data without transport changes. That architectural result remains in
the generic framework; JEI support itself is no longer an included or supported base feature.

## D025: Live native recipes are the deterministic base and fallback

**Status:** Accepted

`FabricRecipeProvider` reads the integrated server's resolved live `RecipeManager` on its owning
thread. That manager is the gameplay-authoritative composition of vanilla definitions and active
datapack/Fabric-mod additions, replacements, and removals. Thread does not ship or consult a static
vanilla recipe catalog.

`CompositeRecipeProvider` first captures this native result so session rejection and native safety
limits cannot be bypassed. It then considers optional recipe contributions in stable integration-ID
order. The first successful, non-empty result replaces the native result for that item. Empty
results, controlled failures, runtime exceptions, and linkage errors decline the item and preserve
the captured native result unchanged.

This policy gives a future optional recipe contribution deterministic precedence for items it can
represent while preserving the native manager whenever the contribution is empty, unavailable, or
faulty. Base Thread registers no optional recipe provider. `CraftingService`, `CraftingPlanner`,
tool handlers, and MCP continue to depend only on `RecipeProvider`.

The packaged test mod proves the boundary with real recipe resources: it adds a two-step custom
recipe chain and replaces the vanilla diamond-pickaxe recipe. The normal packaged run must observe
those resources through all four existing recipe/crafting tools, including complete native
ingredient alternatives. Unit tests prove first-successful-non-empty precedence and fallback with
proof providers, without introducing a third-party dependency.

## D026: Third-party adapters ship as separate Thread Integrations

**Status:** Accepted

The base Thread artifact contains native Minecraft/Fabric support and public integration hooks. It
does not contain third-party gameplay-mod or recipe-viewer APIs, implementations, entrypoints,
metadata, dependencies, or release tasks.

Future JEI, FTB Quests, Create, AE2, storage-network, and similar support belongs in separately
distributed optional mods/packages under the **Thread Integrations** concept. No external adapter
project is created by Slice 12. Fabric discovers external packages through the
`thread:integrations` entrypoint, whose `ThreadIntegrationCandidateProvider` returns metadata-only
`IntegrationCandidate` values. That bootstrap must not link target-mod APIs; configuration,
presence, and version checks still happen before Thread reflectively constructs the named
`ThreadIntegration` implementation. Existing transactional contributions, recipe providers, typed
extensions, capability metadata, failure isolation, and disabling then apply unchanged.

Why:

- keeps base Thread small and dependency-light;
- prevents third-party types and lifecycle requirements from shaping core APIs;
- lets players choose only the adapters their modpack needs;
- allows each adapter to version and test against its own target mod independently;
- preserves native Minecraft's live `RecipeManager` as the base data source.
