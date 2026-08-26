# Thread

Thread is a Minecraft context and tooling layer designed to let AI clients understand a live single-player Minecraft instance through structured, read-only tools.

V1 uses MCP as the first external transport, but the core architecture must not depend on MCP. The same game/context layer should be reusable later by other transports, an in-game assistant, mod integrations, testing tools, or other agent clients.

## V1 outcome

A player can install Thread, launch Minecraft, connect an MCP-capable client, and ask questions that require live game state, such as:

- Am I in a world or sitting in a menu?
- What Minecraft version am I playing?
- What is in my inventory?
- What block am I looking at?
- What entities are near me?
- How do I craft this item?
- Do I currently have the materials for it?

The model gets the answer by calling Thread tools. Thread does not inject large world dumps into prompts and does not act as the AI itself.

## Principles

1. **MCP first, not MCP-bound.** MCP is a transport adapter over internal tools.
2. **Minecraft types stop at the platform boundary.** Core DTOs must not expose `ItemStack`, `PlayerEntity`, `BlockState`, or other loader/game types.
3. **Single-player V1.** V1 only operates on integrated single-player worlds. Multiplayer and dedicated-server support require a separate trust, permission, privacy, and anti-cheat design.
4. **Read-only V1.** No block breaking, inventory mutation, commands, automatic crafting, movement, or world edits.
5. **Explicit capability discovery.** Clients should be able to learn what this installation supports.
6. **Bounded queries.** No arbitrary world scans, forced chunk loads, or unbounded result sets.
7. **Correct threading.** Reads that require Minecraft state must run on the correct client/integrated-server thread.
8. **Test the core without launching Minecraft.** Providers and transports should be mockable.
9. **One loader first.** V1 targets Fabric only. Loader portability is enabled by boundaries, not by building multiple loaders now.
10. **Quality gates are part of the product.** Formatting, linting, tests, Javadocs on public contracts, and CI are established in Slice 0 rather than deferred until release.

## Docs

Read in this order when implementing:

1. [`docs/V1_SCOPE.md`](docs/V1_SCOPE.md)
2. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
3. [`docs/SLICES.md`](docs/SLICES.md)
4. [`docs/TOOL_CONTRACTS.md`](docs/TOOL_CONTRACTS.md)
5. [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)
6. [`docs/TESTING.md`](docs/TESTING.md)
7. [`docs/MCP_NOTES.md`](docs/MCP_NOTES.md)
8. [`docs/ROADMAP.md`](docs/ROADMAP.md)
9. [`docs/DECISIONS.md`](docs/DECISIONS.md)
10. [`docs/INTEGRATIONS.md`](docs/INTEGRATIONS.md)
11. [`docs/MULTIPLAYER_FUTURE.md`](docs/MULTIPLAYER_FUTURE.md)

Codex-specific repository instructions live in [`AGENTS.md`](AGENTS.md).

## V1 slices

| Slice | Purpose |
| --- | --- |
| 0 | Repository foundation and dependency/protocol spike |
| 1 | Core models, provider contracts, tool/context/integration registries |
| 2 | Fabric platform providers for session/game/player/world data |
| 3 | Vanilla V1 tool set and capability discovery |
| 4 | MCP transport adapter over the tool registry |
| 5 | Configuration, security boundaries, limits, and resilience |
| 6 | End-to-end validation, packaging, docs, and V1 release readiness |
| 7 | Deterministic inventory-to-recipe crafting intelligence |
| 8 | Bounded recursive crafting plans with cycle-safe explanations |
| 9 | Optional mod integration discovery, isolation, and typed contribution framework |
| 10 | Optional JEI recipe integration through the existing crafting tools |
| 11 | Live native recipe fallback and deterministic optional-provider precedence |

A slice is complete only when its acceptance criteria in `docs/SLICES.md` pass.

## Install

Thread 0.1.0 requires Minecraft 26.2, Java 25, Fabric Loader 0.19.3 or newer, and Fabric API
0.155.0+26.2 or newer for Minecraft 26.2. JEI is optional; Thread supports JEI
`30.26.0.182` through compatible `30.x` releases when it is installed.

1. Install the required Minecraft, Fabric Loader, and Fabric API versions.
2. Download `thread-0.1.0.jar` and `thread-0.1.0.jar.sha256` from the matching GitHub release.
3. Verify the checksum, then copy only `thread-0.1.0.jar` into the instance's `mods` folder.
4. Launch Minecraft and confirm the log contains `Thread 0.1.0 initialized` and
   `Thread MCP listener started at http://127.0.0.1:25580/mcp`.

To include modpack-aware recipes, install JEI `30.26.0.182` or a newer compatible `30.x` Fabric
build in the same instance. Thread remains fully functional when JEI is absent or disabled: the
native provider reads Minecraft's live recipe manager, including active datapack and Fabric-mod
recipe additions, replacements, and removals.

PowerShell checksum verification:

```powershell
(Get-FileHash .\thread-0.1.0.jar -Algorithm SHA256).Hash.ToLower()
Get-Content .\thread-0.1.0.jar.sha256
```

See [Installation](docs/INSTALLATION.md) for Linux/macOS verification and launcher-specific paths.

## Connect an MCP client

Thread exposes an MCP Streamable HTTP endpoint at
`http://127.0.0.1:25580/mcp`. A Codex configuration example is:

```toml
[mcp_servers.minecraft]
enabled = true
url = "http://127.0.0.1:25580/mcp"
```

Restart Codex and open a new task after changing its MCP configuration. Thread must be running in
Minecraft before the client connects. Codex initializes the connection, receives Thread's server
identity/instructions, and discovers thirteen read-only `minecraft.*` tools. Thread also retains the
stateless MCP `2026-07-28` discovery flow; neither flow creates protocol sessions.

## Configuration

Thread creates `config/thread.json` on first client startup. Every field other than
`schemaVersion` may be omitted to retain its safe default:

```json
{
  "schemaVersion": 1,
  "mcpEnabled": true,
  "mcpBindHost": "127.0.0.1",
  "mcpPort": 25580,
  "enabledTools": ["minecraft.*"],
  "disabledIntegrations": [],
  "maxEntityRadius": 64.0,
  "maxEntityResults": 128,
  "maxItemSearchResults": 64,
  "maxRequestBytes": 1048576,
  "gameThreadTimeoutMillis": 5000,
  "maxConcurrentRequests": 8
}
```

`enabledTools` accepts exact IDs and namespace wildcards such as `minecraft.*`; an empty array
exposes no tools. Disabled tools are never registered, so they are absent from MCP discovery and
capability results. V1 accepts only the explicit loopback hosts `127.0.0.1`, `localhost`, and `::1`.
Thread also applies hard ceilings to every configurable safety limit. Invalid existing files are
preserved for correction, logged without their contents, and replaced in memory by safe defaults
for that launch.

`disabledIntegrations` accepts exact stable integration IDs. Thread checks this list before target
mod/version detection and before resolving an optional integration implementation class.

Set `"mcpEnabled": false` to run Thread without opening a listener. The rest of the mod initializes
normally, so this is a clean supported state rather than a startup failure.

## Troubleshooting

- **The client cannot connect:** verify Minecraft is running, the startup log shows the listener,
  and the client URL is exactly `http://127.0.0.1:25580/mcp`.
- **The port is already in use:** stop the other process or choose another `mcpPort`, then update the
  MCP client URL to match.
- **Fabric reports incompatible mods:** use the exact Minecraft 26.2 build of Fabric API. A Fabric
  API build for another Minecraft version is not interchangeable.
- **Gameplay tools return `UNSUPPORTED`:** enter an integrated single-player world. Status
  and game-info discovery remain available in menus and unsupported multiplayer.
- **A tool is missing:** check `enabledTools`; disabled tools are omitted from discovery.
- **Configuration is rejected:** read the logged validation message, correct
  `config/thread.json`, and restart. Thread never logs the file contents.

See [Installation](docs/INSTALLATION.md) for the complete troubleshooting guide.

## Known V1 limitations

- Fabric client only; no Forge/NeoForge or dedicated-server build.
- Single-player and read-only. Multiplayer gameplay queries, commands, movement, automatic crafting, and
  world/inventory changes are intentionally rejected or absent.
- Nearby-entity queries only inspect already-loaded state and never force-load chunks.
- No authentication or remote binding. The server is intentionally restricted to loopback.
- JEI is the only recipe-viewer integration. REI and EMI are not queried, and recipe layouts with
  non-item inputs, multiple outputs, missing stable IDs, or other data Thread cannot represent are
  skipped safely.
- No in-game assistant UI ships yet.
- `minecraft.can_craft` and `minecraft.get_missing_ingredients` compare one recipe execution with
  the current 36-slot main inventory.
- `minecraft.get_crafting_plan` recursively explains intermediate recipes and final raw shortages
  using deterministic local choices, one shared inventory ledger, active-path cycle detection, and
  hard depth/work limits. It does not inspect nearby storage, account for crafting stations/fuel,
  globally optimize every recipe combination, or perform crafting actions.

## Development and release

The Gradle Wrapper is the supported build entry point. Run the complete local quality gate with:

```bash
./gradlew spotlessCheck check build
```

Run the live development test with `./gradlew runClientGameTest`. Run the clean-install proof against
the packaged mod with `./gradlew runProductionClientGameTest`, and verify disabled MCP startup with
`./gradlew runMcpDisabledProductionClientGameTest`. The packaged JEI checks are
`./gradlew runJeiProductionClientGameTest` and
`./gradlew runJeiDisabledProductionClientGameTest`. `./gradlew releaseBundle` writes the validated
runtime JAR and SHA-256 file to `build/release/`.

V1 uses semantic versions in `gradle.properties` and matching `vMAJOR.MINOR.PATCH` Git tags. A tag
push runs the full gate, packaged-client tests, version match check, and GitHub release upload. See
[Release process](docs/RELEASE.md), [Testing](docs/TESTING.md), and the
[manual MCP smoke checklist](docs/MANUAL_SMOKE_TEST.md).
