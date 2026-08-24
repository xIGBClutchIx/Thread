# Thread V1 Work Slices

Each slice should be handed to Codex separately. Finish and verify one before starting the next.

---

## Slice 0: Repository foundation and protocol spike

### Goal

Create a clean Fabric/Java project and resolve the few dependency decisions that would otherwise leak into later work.

### Work

- initialize Fabric mod project
- choose and pin one stable Minecraft/Fabric target
- configure the required Java toolchain
- configure Spotless formatting and pin the chosen Java formatter version
- configure Checkstyle with a small Thread-owned ruleset
- add `.editorconfig`
- configure JUnit/unit testing and CI-friendly Gradle lifecycle tasks
- add the initial GitHub Actions CI workflow for push/PR/manual runs
- optionally add low-noise Dependabot configuration for Gradle/GitHub Actions
- create package boundaries from `ARCHITECTURE.md`
- add minimal Thread config model and logging
- verify a development client launches and Thread logs its version
- investigate current MCP implementation options for the JVM
- record the selected V1 MCP approach in `DECISIONS.md`

### MCP spike requirement

The current MCP protocol revision and current Java SDK support may not be aligned. Confirm the actual versions before implementation. Whatever is chosen must remain isolated behind `transport/mcp`.

Prefer current Streamable HTTP semantics. Do not build new code around legacy HTTP+SSE.

### Deliverables

- bootable Fabric mod
- passing test task
- pinned dependency/tool versions
- Spotless + Checkstyle + `.editorconfig` baseline
- GitHub Actions CI workflow
- minimal config/logging
- documented MCP implementation decision

### Acceptance criteria

- `gradlew spotlessCheck check build` passes from a clean checkout
- a plain `gradlew build` includes the required formatting/lint/test quality gates
- GitHub Actions CI runs the same required build/quality path
- formatting violations fail CI instead of being rewritten there
- dev client launches
- Thread logs mod/Minecraft/loader versions
- no MCP classes are referenced outside the MCP transport package/module
- no game-state tool behavior is implemented yet

---

## Slice 1: Core contracts and registries

### Goal

Build the loader- and transport-independent foundation.

### Work

Implement:

- core DTO conventions
- `ToolId`
- `ToolResult`
- structured `ToolError`
- `GameTool` abstraction
- `ToolRegistry`
- `ContextProvider` / `ContextRegistry`
- `GameIntegration` / `IntegrationRegistry`
- provider contracts for game, player, world, and recipes
- capability metadata foundation
- serialization/schema strategy used by tools

Add fake/test implementations where useful.

### Deliverables

A tool can be registered, discovered, validated, invoked by ID, and serialized without Minecraft or MCP running.

### Acceptance criteria

- duplicate tool IDs are rejected deterministically
- unknown tool invocation returns a structured error
- invalid input returns a structured error
- successful results serialize consistently
- fake providers can drive unit tests
- core package has no Fabric, Minecraft, or MCP imports

---

## Slice 2: Fabric live-state providers

### Goal

Connect Thread provider contracts to a real running Minecraft instance without exposing Minecraft types to the core.

### Work

Implement Fabric-backed providers for:

- application/session status (menu, loading, single-player world, multiplayer)
- game/version/loader info
- player status
- inventory
- equipment
- dimension/position
- targeted block
- nearby loaded entities
- vanilla item registry access
- vanilla recipes

Add a game-thread execution abstraction for safe reads initiated by external threads.

### Important constraints

- do not force-load chunks
- do not scan beyond configured bounds
- no world mutations
- convert Minecraft values into Thread DTOs at provider boundaries
- handle menus/loading screens/no-world states cleanly

### Deliverables

Provider calls return real data during a development game session.

### Acceptance criteria

- session status can be queried with no world/player loaded
- local player absent returns a defined unavailable result/error
- world absent returns a defined unavailable result/error
- inventory registry IDs/counts are correct
- target block is null/not-available when nothing valid is targeted
- entity query obeys radius and count limits
- providers do not return Minecraft/Fabric types
- external-thread provider calls are marshalled safely to the game thread where required

---

## Slice 3: Vanilla V1 tools

### Goal

Expose the useful V1 gameplay surface through the internal tool registry, with no MCP dependency.

### Tools

Implement the contracts defined in `TOOL_CONTRACTS.md`:

- `minecraft.get_status`
- `minecraft.get_game_info`
- `minecraft.get_player`
- `minecraft.get_inventory`
- `minecraft.get_equipment`
- `minecraft.get_target_block`
- `minecraft.get_nearby_entities`
- `minecraft.get_recipe`
- `minecraft.search_items`
- `minecraft.get_capabilities`

Register these through a built-in `VanillaIntegration`.

### Deliverables

The complete V1 tool catalog works through direct registry calls/tests.

### Acceptance criteria

- every tool has description, input schema, output schema/model, tests, and useful Javadocs on public contracts where applicable
- every tool returns structured data rather than prose
- tool descriptions are sufficient for an LLM to select them correctly
- capability output accurately reflects registered tools/integrations
- recipe lookup uses live game recipe data for the running instance
- item search is bounded and deterministic enough for stable tests
- no MCP imports exist in tool implementations

---

## Slice 4: MCP transport

### Goal

Expose the existing Thread tool registry to MCP clients without moving game logic into the transport.

### Work

- MCP server lifecycle tied to mod lifecycle
- current supported Streamable HTTP/local HTTP transport
- loopback default bind
- tool listing/discovery
- tool schema conversion
- tool invocation -> `ToolRegistry.invoke`
- structured result mapping
- structured error mapping
- server identity/version metadata
- clean shutdown/restart behavior where practical

### Architecture rule

The MCP adapter may translate protocol types, schemas, and errors. It may not call Minecraft APIs or provider implementations directly.

Conceptually:

```text
MCP tools/list -> ToolRegistry metadata
MCP tools/call -> ToolRegistry invocation
```

### Deliverables

A real external MCP client can discover and invoke Thread tools against a running game.

### Acceptance criteria

- client connects on loopback
- client sees the expected V1 tool catalog
- `minecraft.get_status` works through MCP from the main menu and a loaded world
- `minecraft.get_game_info` returns live data through MCP
- `minecraft.get_inventory` returns live data through MCP
- invalid calls return protocol-appropriate errors without crashing Thread
- disconnecting a client does not affect Minecraft
- shutting down Minecraft cleanly stops the MCP listener
- MCP SDK/protocol types remain isolated to the transport layer

---

## Slice 5: Configuration, limits, and resilience

### Goal

Make V1 safe and predictable enough to distribute.

### Work

Configurable settings:

- MCP enabled/disabled
- bind host, with loopback default
- port
- enabled tool groups or individual tools
- max entity radius
- max entity results
- max item search results
- request/body size limit where transport supports it

Hardening:

- reject unsafe/non-loopback bind unless user explicitly opts in, or disallow it entirely for V1
- sanitize/log errors without dumping excessive player state
- timeout/cancellation behavior around game-thread dispatch
- result truncation metadata
- graceful behavior while joining/leaving worlds
- no tool call should crash the client/game process

### Acceptance criteria

- default installation listens only on loopback
- disabled tools are absent from discovery
- limits are enforced server-side, not trusted to clients
- malformed/oversized requests fail safely
- game-thread timeout produces a controlled error
- world unload during a request does not crash the game

---

## Slice 6: End-to-end V1 and release readiness

### Goal

Prove the product experience and leave a maintainable baseline for V2.

### Work

- end-to-end tests where practical
- manual MCP-client smoke test checklist
- clean-install test
- packaged mod artifact
- user README/config example
- developer architecture notes kept current
- troubleshooting section
- known limitations
- release/versioning convention
- tag-driven GitHub release build workflow
- release artifact/checksum handling

### Required manual scenarios

With Minecraft running, verify an MCP-capable client can answer using Thread tools:

1. From the main menu: what state is Minecraft currently in?
2. After loading a single-player world: am I in a supported gameplay session?
3. What Minecraft version am I playing?
4. What is my current health/hunger?
5. What is in my inventory?
6. What equipment am I wearing/holding?
7. What block am I looking at?
8. What entities are within 16 blocks?
9. What is the recipe for a diamond pickaxe?
10. Do I have the materials to make a diamond pickaxe?

Observe the client tool trace where possible and verify it called Thread rather than answering from generic knowledge alone.

### V1 definition of done

- clean build succeeds
- formatter, linter/static checks, and tests pass locally and in GitHub Actions
- public Thread API/extension contracts have useful Javadocs and non-obvious implementation decisions are commented
- tag-driven release workflow produces the validated JAR artifact
- mod loads without another Thread component installed
- MCP is disabled cleanly when configured off
- MCP client discovers the V1 tools
- all ten manual scenarios work
- all tools are read-only
- no arbitrary chunk loading/scanning occurs
- no core dependency on Fabric or MCP has appeared
- docs match implementation
- V1 release artifact can be installed by another person without repository knowledge
