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
- `ThreadIntegration` / `IntegrationRegistry`
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
- all required manual scenarios work
- all tools are read-only
- no arbitrary chunk loading/scanning occurs
- no core dependency on Fabric or MCP has appeared
- docs match implementation
- V1 release artifact can be installed by another person without repository knowledge

---

## Slice 7: Deterministic crafting intelligence

### Goal

Let MCP clients ask whether the current player inventory can satisfy a live recipe and receive an
exact, deterministic explanation of missing ingredients.

### Work

Add:

- `minecraft.can_craft`
- `minecraft.get_missing_ingredients`
- a transport-independent `CraftingService` over the existing player and recipe providers
- crafting assessment DTOs and explicit JSON schemas
- deterministic allocation across overlapping item/tag alternatives

Assess every recipe variant independently, including repeated recipe IDs and shaped/shapeless
types represented by the recipe layer. Report stable variant ordinals plus required, allocated,
available, and missing counts. Merge duplicate ingredient groups defensively.

This slice remains read-only and single-player only. It does not craft items, recurse through
intermediate recipes, inspect nearby storage, or add JEI/EMI/REI integration.

### Acceptance criteria

- at least one satisfied recipe makes the item craftable
- every relevant recipe variant reports its own craftability and ingredient counts
- alternative ingredients consume each inventory unit at most once across a variant
- expanded tag alternatives retain their source tag IDs
- craftable, missing, multiple-variant, shaped/shapeless, duplicate-requirement, empty-inventory,
  no-recipe, no-world, and multiplayer cases have automated coverage
- both tools are discoverable and callable through the unchanged MCP lifecycle
- the packaged single-player game test proves a real diamond-pickaxe assessment through MCP
- formatting, lint, unit/integration tests, clean build, and both packaged client tests pass

## Slice 8: Bounded recursive crafting plans

### Goal

Explain the intermediate crafts and final raw shortages needed to produce one target item from the
current main inventory, without hanging on cyclic or pathological recipe graphs.

### Work

Add:

- `minecraft.get_crafting_plan`
- a transport-independent `CraftingPlanner` that reuses `CraftingService` allocation
- deterministic local recipe-variant and ingredient-alternative selection
- post-order crafting steps, final raw-material shortages, and structured safety issues
- active-path cycle detection plus maximum depth, step, branch, and quantity limits

The planner uses one inventory/surplus ledger across the selected plan so separate branches cannot
claim the same item. It may cache detached recipe definitions, but never caches a resolution result
whose answer depends on mutable inventory state. Candidate recipes and alternatives are compared
locally by safety issues, unresolved amount, raw shortages, step count, and canonical order. This is
stable and bounded, not exhaustive global optimization.

Raw items with no recipe are reported as materials to acquire, allowing later crafting steps to
remain visible. A cycle, maximum depth, or work-limit branch is not treated as obtainable; the issue
contains its affected item, count, and active path.

This slice remains single-player and read-only. It does not perform crafting, inspect equipment or
nearby storage, model workstation/fuel feasibility, or add JEI/EMI/REI integration.

### Acceptance criteria

- simple and multi-level recipes produce dependency-first steps
- partial inventory and repeated cross-branch ingredients use one non-overlapping supply ledger
- recipe variants and alternatives are selected deterministically, including repeated recipe IDs
- active-path tracking catches direct, indirect, and tag/alternative cycles without rejecting valid
  reuse in separate branches
- a non-cyclic variant wins over a cyclic variant when available
- maximum depth and total planning limits terminate pathological graphs with structured issues
- empty-inventory, no-recipe, no-world, and multiplayer behavior has automated coverage
- the tool is discoverable and callable through the unchanged MCP lifecycle
- the packaged single-player test plans a crafting table recursively from a real oak log through MCP
- formatting, lint, unit/integration tests, clean build, and both packaged client tests pass

---

## Slice 9: Optional mod integration framework

### Goal

Let Thread activate compatible optional mod integrations without importing third-party APIs into
core or resolving absent integration classes during startup.

### Work

Add:

- `ThreadIntegration` and a transactional `IntegrationContext`
- metadata-only `IntegrationCandidate` discovery over loader-neutral mod/version checks
- class-name-based loading only after enabled, present, and compatible checks pass
- typed extension registration for tools, contexts, recipe providers, Fabric block/block-entity
  enrichment, Fabric entity enrichment, and bounded capability metadata
- deterministic integration/contribution ordering and duplicate rejection
- isolated activation and runtime contribution failures
- a test-only reflective proof integration

The built-in vanilla integration remains required and owns the existing thirteen tools. Optional
recipe providers are considered only after a successful guarded native read, so they cannot replace
session guards. Minecraft-facing enrichment points remain in Fabric packages and return detached
Thread DTOs. This slice adds no generic lifecycle beyond startup registration and ships no JEI,
EMI, REI, FTB Quests, Create, Mekanism, or other substantial mod support.

### Acceptance criteria

- present compatible integrations activate; absent, disabled, and incompatible candidates skip
  without class loading
- duplicate IDs, constructor/linkage failures, and registration failures do not partially commit or
  stop healthy optional candidates
- tools, contexts, recipe providers, typed platform extensions, and metadata can be contributed
  independently
- active integration IDs, versions, contribution metadata, and target-mod metadata appear through
  `minecraft.get_capabilities`
- recipe, block, block-entity, and entity contribution failures preserve vanilla behavior
- optional implementation classes are referenced by name only from the discovery catalog
- the packaged game test proves the registry initializes with vanilla behavior unchanged
- architecture boundaries, formatting, lint, unit/integration tests, clean build, and both packaged
  client tests pass

---

## Slice 10: Optional JEI recipe integration

### Goal

Prove the optional integration framework against one supported third-party recipe viewer while
letting the existing recipe and crafting tools understand safe modpack-aware recipes.

### Work

Add:

- a metadata-only `jei` candidate for JEI `30.26.0.182` through compatible `30.x` Fabric builds
- a Fabric integration loader that injects only the client-thread executor, provider limits, and
  DTO mapper after candidate discovery
- a JEI lifecycle bridge and bounded recipe-layout adapter isolated under
  `platform.fabric.integration.jei`
- conversion of stable single-item-output, item-input recipes with alternatives, counts, and tag
  provenance into the existing Thread recipe model
- safe skipping for missing IDs, custom/non-item inputs, ambiguous or multiple outputs, malformed
  layouts, and excessive result/slot/alternative counts
- packaged tests for JEI present, absent, and installed-but-disabled behavior

The adapter contributes only an `IntegrationRecipeProvider`; it adds no MCP tools and does not
change MCP transport. The guarded vanilla provider runs first. JEI runtime or layout failures are
isolated so supported single-player vanilla behavior remains available.

### Acceptance criteria

- supported JEI activates after enabled/presence/version checks and appears in capabilities
- absent or disabled JEI leaves startup, the thirteen tools, and vanilla recipes unchanged
- no JEI implementation class is resolved by Thread when the candidate is absent or disabled
- JEI API types stay within the JEI integration package and never enter core DTOs/services or MCP
- supported modified recipes preserve stable IDs, variants, item alternatives, counts, and tags
- unsupported recipe types/layouts are skipped without invented data or failed vanilla queries
- `get_recipe`, `can_craft`, `get_missing_ingredients`, and the recursive planner consume the same
  contributed recipes without duplicate JEI-specific tools
- normal, MCP-disabled, JEI-present, and JEI-disabled packaged client tests pass
- formatting, lint, unit/integration tests, clean build, and release artifact checks pass

---

## Slice 11: Live native recipe fallback and deterministic precedence

### Goal

Prove that Minecraft's resolved runtime recipe system remains Thread's authoritative base source
when JEI is absent, disabled, unavailable, or cannot represent an item, while making optional
provider precedence explicit.

### Work

Add:

- an explicit first-successful-non-empty policy for optional recipe providers in stable
  integration-ID order
- unchanged native fallback for empty, failed, crashing, or unavailable optional providers
- documentation that `FabricRecipeProvider` reads the integrated server's live `RecipeManager`, not
  a static vanilla catalog
- game-test-mod recipe resources containing a custom two-step recipe chain and a vanilla recipe
  replacement
- packaged assertions covering native recipe lookup, alternatives, overrides, craftability,
  missing ingredients, recursive planning, JEI absence, JEI disablement, and JEI precedence
- JEI conversion through `IRecipeSlotView.getAllIngredients()` so cycling display state cannot omit
  valid alternatives

The base provider still runs first so the centralized single-player guard and native query limits
remain authoritative. A non-empty optional result replaces the base result only for that item. No
core crafting service or MCP tool knows which source was selected.

### Acceptance criteria

- absent and disabled JEI return recipes from the live native manager
- the native path observes a game-test-mod recipe addition and a replacement of a vanilla recipe
- `get_recipe`, `can_craft`, `get_missing_ingredients`, and recursive planning all consume the
  custom native recipes
- native ingredient alternatives remain complete and deterministic
- enabled JEI takes precedence for items it can represent, including JEI-only test recipes
- empty or failed optional results restore the already-captured native result unchanged
- core and MCP remain decoupled from recipe-source choice and MCP transport is unchanged
- safety, cycle, depth, query, and plan limits remain enforced
- formatting, lint, unit/integration tests, clean build, release verification, and all four packaged
  client tests pass
