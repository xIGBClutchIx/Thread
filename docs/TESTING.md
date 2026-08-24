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

The isolated Fabric client game test under `src/gametest` creates a temporary single-player world
and invokes all ten vanilla V1 tools. It covers external-thread dispatch, exact inventory and
equipment IDs/counts, a deterministic target block, an already-loaded entity within 16 blocks,
diamond-pickaxe recipe access, inventory/recipe material comparison, item search, capability
discovery, and MCP calls from both menu and supported-world states.

`./gradlew runClientGameTest` runs this proof against development outputs.
`./gradlew runProductionClientGameTest` instead loads the installable runtime JAR plus an isolated
game-test JAR; production classes do not leak in through the harness. The game-test source set is
never packaged in the runtime mod.

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
- required per-request capabilities metadata, response identity stamps, and cache hints
- rejection of the retired initialization, session-header, and cancellation-notification flow
- Base64-encoded MCP names
- loopback Origin, content type, request size, and listener bind enforcement
- bounded concurrent request rejection without queueing more tool work
- repeated independent clients, idempotent shutdown, and same-port restart

Do not require Minecraft for most MCP tests.

The Fabric client game test additionally starts the production listener at
`http://127.0.0.1:25580/mcp`. It performs `server/discover` and `tools/list`, verifies all ten
release scenarios through real HTTP `tools/call` requests, and confirms an invalid MCP call does
not stop the listener. Normal client shutdown must log that the listener stopped.

`./gradlew runMcpDisabledProductionClientGameTest` writes an isolated config with MCP disabled,
loads the packaged runtime JAR, proves no listener was started, and verifies that normal Thread tool
registration still completes.

Slice 5 unit coverage also verifies persistent configuration creation/validation, pre-registration
tool filtering, server-enforced provider limits, game-thread timeout cancellation, and controlled
shutdown-time dispatch rejection. Together, the timeout and rejected-dispatch cases model an
in-flight request spanning a world or integrated-server teardown without allowing the exception to
escape the tool boundary.

## Manual end-to-end smoke test

Required before V1 release. Follow [the ten-scenario checklist](MANUAL_SMOKE_TEST.md) against the
downloaded release JAR and retain client tool traces. The manual pass validates model tool choice
and the real MCP-capable client integration that an automated HTTP harness cannot represent.

## Quality gate

The normal Gradle lifecycle is part of testing. From a clean checkout, formatting, lint/static checks, compilation, and unit tests must all be reproducible. CI should execute the same build path developers/agents use locally rather than maintaining a separate hidden validation script.

Expected preflight:

```bash
./gradlew clean spotlessCheck check build
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

The local release-equivalent gate is documented in [RELEASE.md](RELEASE.md). It adds both packaged
client runs, the tag/version check, runtime artifact inspection, and checksum generation to the
normal quality gate.
