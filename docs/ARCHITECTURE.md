# Thread V1 Architecture

## High-level design

```text
                     External AI client
                            |
                            | MCP
                            v
                  +-------------------+
                  |   MCP Transport   |
                  +---------+---------+
                            |
                            | ToolRegistry API
                            v
                  +-------------------+
                  |   Thread Core     |
                  |-------------------|
                  | Tool Registry     |
                  | Context Registry  |
                  | Integration Reg.  |
                  | Crafting Service  |
                  | Crafting Planner  |
                  | DTOs / Errors     |
                  +---------+---------+
                            |
                            | provider contracts
                            v
                  +-------------------+
                  | Fabric Platform   |
                  |-------------------|
                  | GameProvider      |
                  | PlayerProvider    |
                  | WorldProvider     |
                  | RecipeProvider    |
                  +---------+---------+
                            |
                            v
                       Minecraft
```

## Dependency direction

Dependencies point inward:

```text
Fabric/Minecraft -> Thread Core <- MCP Transport
```

The core does not import either Fabric/Minecraft implementation classes or MCP SDK classes.

## Java source conventions

Thread V1 uses normal Java source files only. Do not create `package-info.java` files. Package-level architectural documentation belongs in `docs/`, while public API/type intent should be documented directly on the relevant class or interface when needed.

## Core layers

### Models

Immutable, serialization-friendly values such as:

- `SessionStatus`
- `GameInfo`
- `PlayerStatus`
- `InventorySnapshot`
- `ItemStackInfo`
- `ItemDurabilityInfo`
- `ItemEnchantmentInfo`
- `ItemComponentsInfo`
- `EquipmentSnapshot`
- `BlockInfo`
- `BlockEntityInfo`
- `EntityInfo`
- `RecipeInfo`
- `CapabilityInfo`
- `ToolError`

Registry IDs such as `minecraft:iron_ingot` are preferred over display names as canonical identifiers.
Inventory, equipment, recipe results, and safe container inspection share `ItemStackInfo`; selected
component fields are explicit and bounded rather than a generic Minecraft component/NBT mirror.

Thread groups model types by the game domain they describe:

- `model.capability`: capability and integration snapshots
- `model.crafting`: craftability, allocation, recursive plan, shortage, and safety-issue results
- `model.game`: runtime and session state
- `model.item`: item identity, stacks, components, and search
- `model.player`: player status, inventory, and equipment
- `model.recipe`: recipe lookup and ingredient data
- `model.world`: positions, blocks, block entities, and nearby entities
- `model.validation`: shared constructor validation for model records

Tool-only request types such as `EmptyInput` belong in `core.tool`.

### Services

`CraftingService` is a transport-independent application service over `PlayerProvider` and
`RecipeProvider`. It reads detached recipe and main-inventory snapshots, assesses every recipe
variant independently, and returns `model.crafting` DTOs. Tool handlers only delegate to this
service; MCP never performs recipe comparison.

The service treats each ingredient group's `itemIds` as the complete resolved alternatives. A
source tag remains in `tagIds` as provenance, while the Fabric recipe adapter expands its current
members into `itemIds`. A deterministic maximum-flow allocation assigns each inventory unit to at
most one ingredient requirement. This prevents overlapping alternatives from double-counting the
same stack and gives constrained groups priority without sacrificing the maximum satisfied count.
Identical ingredient groups are merged defensively, and recipe variants receive stable one-based
ordinals after canonical sorting; recipe IDs are not assumed unique.

The direct assessment covers one execution of a represented recipe using only the player's 36
main-inventory slots. It does not inspect equipment or nearby storage, check workstations/fuel, or
perform crafting actions.

`CraftingPlanner` recursively resolves ingredient recipes over the same provider contracts and calls
`CraftingService.assessRecipe` for every planned recipe execution. One mutable supply ledger follows
the selected plan so inventory and crafted surplus cannot be double-counted across branches. Recipe
definitions may be cached, but inventory-dependent resolution is never memoized.

Cycles are detected against the active dependency path, not a global visited set, so a material may
legitimately appear in separate branches. Maximum depth, step count, explored-branch count, and
scaled-quantity limits provide additional termination boundaries. Raw leaves become final shortages;
cycle/depth/limit branches remain unresolved and produce structured issues with their paths.

Recipe variants and ingredient alternatives are evaluated in canonical order and selected locally
by fewest safety issues, unresolved units, raw shortages, then steps. This is deterministic and
bounded rather than an exhaustive global optimizer. Steps are returned in dependency-first order.

### Providers

Providers describe what Thread needs from Minecraft, not how Fabric exposes it.

Conceptual contracts:

```java
public interface GameProvider {
    SessionStatus sessionStatus();
    GameInfo gameInfo();
}

public interface PlayerProvider {
    ToolResult<PlayerStatus> status();
    ToolResult<InventorySnapshot> inventory();
    ToolResult<EquipmentSnapshot> equipment();
    ToolResult<Optional<BlockInfo>> targetBlock();
}

public interface WorldProvider {
    ToolResult<NearbyEntityResult> nearbyEntities(NearbyEntityQuery query);
}

public interface RecipeProvider {
    ToolResult<List<RecipeInfo>> recipesFor(String itemId);
    ToolResult<ItemSearchResult> searchItems(String query, int limit);
}

public interface GameThreadExecutor {
    <T> T call(Supplier<T> operation);
}
```

Exact Java signatures may change, but the boundary must remain.

### Tool registry

A tool owns:

- stable ID
- description
- input schema/model
- output schema/model
- capability metadata
- execution function

Conceptually:

```java
public interface GameTool<I, O> {
    ToolId id();
    String description();
    JsonCodec<I> inputCodec();
    JsonCodec<O> outputCodec();
    ToolCapabilities capabilities();

    ToolResult<O> execute(I input);
}
```

Do not over-engineer generic reflection if explicit serializers/schemas are simpler.

### Serialization and schemas

Thread contracts use explicit JSON Schema documents paired with small Gson codecs. Schemas are not
inferred from Java reflection: each tool or context provider owns the schema it exposes during
discovery, and the same schema validates input before execution and output after serialization.

Gson 2.14.0 is already supplied on the compile, test, and client runtime classpaths by the pinned
Minecraft 26.2 dependency. Thread uses that runtime library rather than introducing a second JSON
stack. Gson types are confined to `core.serialization` and the core registry invocation boundary;
public game DTOs and provider contracts remain plain Java and MCP-independent.

### Context registry

V1 context should remain small. It can expose static/semi-static information such as:

- Thread version
- Minecraft version
- loader and loader version
- active integrations
- available capabilities

Dynamic state such as inventory should be requested through tools rather than continuously injected as context.

### Integration registry

Thread ships a clean optional-integration framework while still containing only the built-in
vanilla gameplay integration. No substantial third-party mod support ships in this slice.

Conceptually:

```java
public interface ThreadIntegration {
    IntegrationId id();
    String version();
    String description();
    void register(IntegrationContext context);
}
```

`IntegrationContext` is a transactional contribution surface. An integration may register only the
pieces it implements:

- read-only `GameTool` values;
- bounded `ContextProvider` values;
- additional `IntegrationRecipeProvider` values;
- typed core- or platform-owned extension contributions;
- bounded integration-specific capability metadata.

The callback has no speculative startup/shutdown lifecycle. Contributions become visible only
after the callback and all duplicate/contract validation succeeds. A failed callback therefore
cannot leave a partial tool, context, recipe provider, or enrichment registration behind.

Optional integrations are declared as `IntegrationCandidate` metadata containing a stable
integration ID, target mod ID, compatible version requirement, and implementation **class name**.
Fabric Loader metadata is checked in this order:

1. the integration is enabled by configuration;
2. the target mod is loaded;
3. the installed version is compatible;
4. only then is the implementation class resolved and constructed.

The catalog must not import an optional implementation or use its class literal. This ordering is
what prevents an absent optional API from causing verification, linkage, or startup failures.
Unavailable, disabled, incompatible, construction-failing, and registration-failing candidates are
reported as isolated activation outcomes; discovery continues with the next stable ID.

The built-in `VanillaIntegration` owns the thirteen V1 `minecraft.*` tools. Fabric startup supplies its
loader-neutral providers, then activates it as a required integration through
`IntegrationRegistry`. The capabilities tool reads the live tool and integration registries at
invocation time. Active integrations expose stable IDs, versions, contribution/target-mod metadata,
and their own bounded metadata rather than a parallel hard-coded feature list.

`IntegrationExtensionRegistry` is type-safe but platform-neutral: an extension point owns a stable
ID plus its contribution contract. Contributions are returned in integration-ID order, preserving
declaration order within one integration. The implemented points are:

- `thread.recipe_provider`: adds detached recipe definitions after the guarded vanilla provider
  succeeds. Optional provider failures are ignored so vanilla recipe behavior and session guards
  remain authoritative.
- `fabric.block_entity_inspector`: produces bounded structured data for a recognized block entity.
- `fabric.block_enricher`: enriches an already-detached target-block snapshot.
- `fabric.entity_enricher`: enriches an already-detached nearby-entity snapshot.

The three Fabric points remain at the platform edge because their contributor contracts receive
Minecraft implementation types on the owning logical thread. They must return Thread DTOs and may
not expose raw Minecraft, third-party, NBT, or component objects. Runtime exceptions and linkage
errors from optional recipe/enrichment contributions are isolated per contributor.

Future integrations may define additional typed extension points and metadata keys. The framework
does not yet implement JEI, EMI, REI, FTB Quests, Create, Mekanism, storage-network, or other
third-party behavior.

## Platform boundary

Fabric code may use Minecraft classes internally, but must convert them before returning across provider boundaries.

Bad:

```java
List<ItemStack> inventory();
```

Good:

```java
InventorySnapshot inventory();
```

This protects:

- tests
- transport independence
- future loader support
- serialization stability

## Session state and preflight

`minecraft.get_status` is the lightweight preflight tool and must remain callable even when no world or local player exists. It answers whether Minecraft is at a menu, loading a world, in a supported single-player session, or connected to unsupported multiplayer.

Suggested states:

- `MAIN_MENU`
- `LOADING_WORLD`
- `SINGLEPLAYER`
- `MULTIPLAYER`

The result should also expose simple booleans such as `worldLoaded`, `playerAvailable`, and `supported`. Avoid leaking transient Minecraft screen class names into the stable contract.

`minecraft.get_game_info` describes the installation/runtime (Minecraft version, Fabric version, Thread version). Gameplay tools require a supported `SINGLEPLAYER` session and should fail through the centralized session guard otherwise.

## Threading model

MCP requests may arrive on HTTP/server threads. Minecraft state must not be read unsafely from those threads.

`GameThreadExecutor` is the loader-neutral synchronous dispatch boundary. Fabric supplies separate adapters for the client and integrated-server threads. Provider operations marshal state reads to their owning thread and return detached DTOs to the requesting transport.

Target-block inspection uses two stages: the client thread captures the camera
raycast target and dimension, then the integrated-server thread reads the already-loaded block,
state, and block entity. A loaded-chunk check precedes that server read, so inspection cannot force
a chunk load. Unresolved loot containers are not opened because reading a slot would mutate world
state by resolving the loot table.

Do not hide unsafe cross-thread reads behind `synchronized`.

## MCP transport

`transport.mcp` implements the narrow MCP `2026-07-28` Streamable HTTP boundary. Its only game
facing dependency is `ToolRegistry`:

```text
HTTP initialize      -> transport-owned server identity/capabilities/instructions
HTTP server/discover -> transport-owned server metadata
HTTP tools/list      -> ToolRegistry.descriptors()
HTTP tools/call      -> ToolRegistry.invoke(...)
```

The adapter owns HTTP, Origin, protocol/header, JSON-RPC, schema, and result/error translation.
It never imports Fabric/Minecraft types or calls providers directly. Requests execute on daemon
transport workers; provider-owned `GameThreadExecutor` implementations remain responsible for
marshalling live reads onto Minecraft's logical threads.

V1 starts one `127.0.0.1` listener after built-in tool registration and closes it from the Fabric
client-stopping event. The same POST endpoint accepts Codex's initialization sequence and the
stateless `2026-07-28` discovery sequence. Initialization returns identity, tool capabilities, and
instructions, then accepts `notifications/initialized`; it does not create session state or mint a
`Mcp-Session-Id`. Legacy HTTP+SSE remains unsupported.

## Client vs logical server state

Minecraft has a logical server even in singleplayer. V1 should be explicit about where each fact comes from.

Examples:

- camera target block: client-derived
- local player's HUD-level state: often client-readable
- loaded entities: bounded reads from the client level's existing entity index
- live recipe definitions and display resolution: integrated logical server

Do not duplicate state unnecessarily. Document side ownership in provider implementations.

## Query safety

World-oriented tools must be bounded:

- maximum radius configured centrally
- maximum result count configured centrally
- use already-loaded/available world state
- do not force-load chunks
- return truncation metadata when a cap is reached

Example:

```json
{
  "radius": 16,
  "truncated": true,
  "entities": []
}
```

## Error model

Tool failures should be useful to machines and humans.

Example:

```json
{
  "code": "PLAYER_NOT_AVAILABLE",
  "message": "No local player is currently available.",
  "retryable": true
}
```

Suggested initial codes:

- `INVALID_INPUT`
- `NOT_AVAILABLE`
- `PLAYER_NOT_AVAILABLE`
- `WORLD_NOT_AVAILABLE`
- `NOT_FOUND`
- `OUT_OF_RANGE`
- `RESULT_LIMIT_EXCEEDED`
- `TIMEOUT`
- `UNSUPPORTED`
- `INTERNAL_ERROR`

## Package shape

The exact Gradle source-set layout can vary, but keep boundaries obvious:

```text
thread/
  core/
    model/
      capability/
      crafting/
      game/
      item/
      player/
      recipe/
      validation/
      world/
    tool/
    context/
    integration/
      vanilla/
    provider/
    serialization/
    service/
    error/
  platform/
    fabric/
      game/
      player/
      world/
      recipe/
      threading/
  transport/
    mcp/
  config/
```

A multi-module Gradle build is optional for V1. Strong package boundaries plus dependency tests may be enough initially. Split modules only if it improves enforcement without slowing iteration.

## Enforced package roots

The V1 implementation uses `me.clutchy.thread` as its Java root:

```text
me.clutchy.thread
  core
    model
      capability
      crafting
      game
      item
      player
      recipe
      validation
      world
    tool
    context
    integration
    provider
    serialization
    service
    error
  platform.fabric
    game
    player
    world
    recipe
    threading
  transport.mcp
  config
```

Packages are added only when a slice gives them behavior; empty marker classes and
`package-info.java` files are not used. Architecture tests enforce these dependency rules as the
packages grow:

- `core` cannot import Fabric, Minecraft, or MCP types;
- model source files must live in one of the documented domain packages;
- MCP SDK types can only appear below `transport.mcp`;
- `transport.mcp` cannot import Fabric or Minecraft types directly.

## Distribution and packaged-runtime proof

The release artifact is one client mod JAR containing both core and platform/transport adapters.
This does not collapse their dependency boundaries: package architecture tests still inspect
production sources, and `verifyReleaseArtifact` inspects the expanded Fabric metadata and JAR
contents before distribution.

Production client tests launch that runtime JAR through Loom's production runner. A separate
`thread-gametest` JAR supplies only the test entrypoint and fixtures, depends on Thread as a normal
mod, and is never copied into the release bundle. This separation proves the published JAR does not
need a second Thread component or development class directories. A second production launch writes
an isolated `mcpEnabled: false` configuration and proves transport disablement does not prevent core
tool registration.
