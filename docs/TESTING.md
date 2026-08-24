# Thread V1 Testing Strategy

## Goals

Thread should be testable at three levels:

1. core tests without Minecraft
2. Fabric/provider tests where practical
3. end-to-end MCP smoke tests against a running development game

The architecture should maximize level 1 because launching Minecraft for every behavior check is slow and brittle.

## Core unit tests

Use fake providers to test tool behavior.

Example fixture concept:

```java
final class FakePlayerProvider implements PlayerProvider {
    private final InventorySnapshot inventory;

    FakePlayerProvider(InventorySnapshot inventory) {
        this.inventory = inventory;
    }

    @Override
    public ToolResult<InventorySnapshot> inventory() {
        return ToolResult.success(inventory);
    }
}
```

Cover:

- tool registration
- duplicate tool IDs
- unknown tools
- input validation
- error serialization
- result serialization
- capability discovery
- session status transitions and preflight behavior
- disabled tool behavior
- query limit clamping
- deterministic item search
- recipe DTO handling, especially ingredient alternatives

## Architecture tests

Where feasible, add automated checks that protect package boundaries.

Examples:

- `core` must not import Fabric packages
- `core` must not import Minecraft packages
- `core` must not import MCP SDK packages
- tool implementations must not import MCP SDK packages
- MCP transport must not import Fabric/Minecraft classes directly

This can be done with module separation, dependency analysis, or lightweight source/package checks. Prefer the simplest reliable mechanism.

## Provider tests

Test conversion logic separately from live access whenever possible.

Examples:

- `ItemStack` -> `ItemStackInfo`
- block state -> `BlockInfo`
- entity -> `EntityInfo`
- recipe -> `RecipeInfo`

Critical edge cases:

- main menu / no world
- world loading
- no player
- multiplayer session rejected by gameplay tools
- player changes dimension
- world unloads during request
- target becomes invalid
- empty inventory
- recipe not found
- entity result cap reached

Slice 2 introduced an isolated Fabric client game test under `src/gametest`, and Slice 3 extends it
through the complete internal tool registry. Run `./gradlew runClientGameTest` to create a temporary
single-player world and invoke all ten vanilla V1 tools, including external-thread dispatch, exact
inventory IDs/counts, loaded-state queries, deterministic item search, capability discovery, and
integrated-server recipe access. The game-test source set is not packaged in the production mod.

## Threading tests

The game-thread dispatcher deserves explicit tests.

Verify:

- work submitted from a non-game thread runs on the expected game thread
- completion/error reaches the caller
- timeouts produce a controlled tool error
- cancellation/late completion cannot corrupt state
- shutdown rejects or drains pending work predictably

## MCP adapter tests

`McpHttpServerTest` starts the real JDK HTTP listener on ephemeral loopback ports against a fake
`ToolRegistry`. It verifies:

- discovery identity/version/capability metadata
- tool list metadata, deterministic registry mapping, and input/output schemas
- structured success and tool-error results
- unknown methods/tools and malformed JSON-RPC requests
- protocol version and mirrored `Mcp-Method`/`Mcp-Name` validation
- Base64-encoded MCP names
- loopback Origin, content type, request size, and listener bind enforcement
- repeated independent clients, idempotent shutdown, and same-port restart

Do not require Minecraft for most MCP tests.

The Fabric client game test additionally starts the production listener at
`http://127.0.0.1:25580/mcp`. It performs `server/discover` and `tools/list`, calls
`minecraft.get_status` from the main menu and a loaded temporary world, calls
`minecraft.get_game_info`, verifies a known live inventory through `minecraft.get_inventory`, and
confirms an invalid MCP call does not stop the listener. Normal client shutdown must log that the
listener stopped.

## Manual end-to-end smoke test

Required before V1 release:

First verify `minecraft.get_status` in the main menu, while loading/entering a world, and in a loaded single-player world. Gameplay tools should only become available in the supported single-player state.

### Environment

- clean Thread config
- development or packaged Fabric instance
- survival world
- known inventory contents
- known nearby entity/block setup
- MCP-capable client connected to Thread

### Scenarios

1. Ask current Minecraft version.
2. Ask current player health/hunger.
3. Ask for inventory summary.
4. Ask what is equipped.
5. Look at a known block and ask what it is.
6. Place/spawn known entities nearby and ask what is nearby.
7. Ask for a vanilla recipe.
8. Put exact recipe ingredients in inventory and ask whether the player can make it.

When the MCP client exposes tool traces, capture which tools were called. The model should use Thread for live-state questions.

## Quality gate

The normal Gradle lifecycle is part of testing. From a clean checkout, formatting, lint/static checks, compilation, and unit tests must all be reproducible. CI should execute the same build path developers/agents use locally rather than maintaining a separate hidden validation script.

Expected preflight:

```bash
./gradlew spotlessCheck check build
```

A plain `./gradlew build` must include all required quality gates.

## Release gate

Do not tag V1 unless:

- clean build passes
- Spotless check passes
- Checkstyle/static checks pass
- GitHub Actions CI passes
- unit tests pass
- architecture boundaries are intact
- manual smoke test passes
- default listener is loopback-only
- all V1 tools are read-only
- docs reflect actual behavior
- tag/release workflow can produce the installable V1 JAR from a clean checkout
