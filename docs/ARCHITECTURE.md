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
- `EquipmentSnapshot`
- `BlockInfo`
- `EntityInfo`
- `RecipeInfo`
- `CapabilityInfo`
- `ToolError`

Registry IDs such as `minecraft:iron_ingot` are preferred over display names as canonical identifiers.

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
    ToolResult<List<ItemInfo>> searchItems(String query, int limit);
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

V1 contains only the built-in vanilla integration, but the extension point exists now.

Conceptually:

```java
public interface GameIntegration {
    String id();
    void register(IntegrationContext context);
}
```

Future integrations can register tools/providers/context without changing MCP code.

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

Do not hide unsafe cross-thread reads behind `synchronized`.

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
- `UNSUPPORTED`
- `INTERNAL_ERROR`

## Package shape

The exact Gradle source-set layout can vary, but keep boundaries obvious:

```text
thread/
  core/
    model/
    tool/
    context/
    integration/
    provider/
    serialization/
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
    tool
    context
    integration
    provider
    serialization
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
- MCP SDK types can only appear below `transport.mcp`;
- `transport.mcp` cannot import Fabric or Minecraft types directly.
