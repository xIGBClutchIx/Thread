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
4. **Read-only V1.** No block breaking, inventory mutation, commands, crafting, movement, or world edits.
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
10. [`docs/MULTIPLAYER_FUTURE.md`](docs/MULTIPLAYER_FUTURE.md)

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

A slice is complete only when its acceptance criteria in `docs/SLICES.md` pass.

## Development baseline

Thread currently targets Minecraft 26.2 with Fabric Loader 0.19.3, Fabric API 0.158.0+26.2,
and Java 25. The Gradle Wrapper is the supported build entry point.

Run the complete local quality gate with:

```bash
./gradlew spotlessCheck check build
```

Use `./gradlew spotlessApply` to format local changes. CI only runs `spotlessCheck`; it never
rewrites source. Launch the development client with `./gradlew runClient`.

Slice 0 intentionally contains no game-state tools or MCP server. It establishes the build,
runtime bootstrap, package boundaries, and protocol decision that later slices build upon.
