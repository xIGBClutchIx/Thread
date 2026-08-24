# Thread Codex Instructions

This file is the implementation contract for agents working in this repository.

## Before changing code

Read:

1. `docs/V1_SCOPE.md`
2. `docs/ARCHITECTURE.md`
3. the active slice in `docs/SLICES.md`
4. `docs/TOOL_CONTRACTS.md` when working on tools or serialization
5. `docs/DEVELOPMENT.md` for source quality, documentation, formatter/linter, and CI rules
6. `docs/MCP_NOTES.md` when working on transport code

Do not implement future roadmap items unless the active slice explicitly requires them.

## Non-negotiable architecture rules

- Core game/tool abstractions must not depend on MCP.
- MCP code must not directly query Minecraft state.
- Minecraft/Fabric classes must not appear in public core DTOs or transport contracts.
- Platform providers are responsible for converting Minecraft objects into Thread DTOs.
- V1 is single-player only. Do not intentionally support live gameplay tools while connected to a multiplayer server.
- V1 is read-only.
- V1 is Fabric-only.
- V1 implementation language is Java. Do not introduce Kotlin or a Kotlin runtime dependency.
- Do not create `package-info.java` files. Put package-level/project documentation in Markdown under `docs/` and use Javadocs on public types where needed.
- V1 has no OpenAI/ChatGPT/Codex SDK dependency. Clients connect through MCP.
- V1 has no custom in-game assistant/chat UI.
- V1 does not integrate JEI, EMI, REI, FTB Quests, Create, AE2, Mekanism, or other third-party mods.
- Do not force-load chunks to satisfy a tool request.
- All scan/query sizes must be bounded.
- Default network binding must be loopback only.
- Game-state reads must respect Minecraft logical side and thread requirements.
- `minecraft.get_status` must remain callable from menus, loading states, supported single-player, and unsupported multiplayer.
- A centralized session guard must reject unsupported multiplayer sessions before gameplay state is exposed by other tools.
- Do not add multiplayer permissions, server trust, remote-player visibility, anti-cheat policy, or dedicated-server behavior in V1.

## Code quality

- Prefer small interfaces and immutable DTOs.
- Keep serialization explicit and stable.
- Give tool errors machine-readable codes plus human-readable messages.
- Avoid global singletons when dependency injection or explicit wiring is practical.
- Keep dependencies minimal.
- Add tests for behavior introduced by the active slice.
- Do not leave acceptance-critical work as TODOs.
- Document public Thread contracts and extension points with useful Javadocs.
- Add implementation comments for non-obvious threading, logical-side, lifecycle, protocol, safety-limit, and compatibility decisions.
- Prefer comments that explain why; do not narrate obvious code.
- Use Spotless as the formatter entry point and Checkstyle for Java lint/style checks as defined in `docs/DEVELOPMENT.md`.
- Do not bypass formatter, linter, compiler, or test gates to make a slice pass.
- Keep public interfaces documented where intent is not obvious.

## Working style

For each slice:

1. Restate the slice goal in your own words.
2. Inspect the existing repository before designing changes.
3. Implement only what is needed for the slice plus small supporting refactors.
4. Run `spotlessApply` when appropriate, then formatting checks, lint/static checks, unit tests, and relevant integration tests.
5. Compare the result against every acceptance criterion.
6. Update docs only when implementation decisions materially differ from the current plan.
7. Report any deliberate deviation and why it was necessary.

If a dependency or protocol version is incompatible with the design, do not leak the workaround into core APIs. Isolate it behind the relevant adapter and document the compatibility issue.

## Definition of done for any slice

A slice is not done because it compiles. It is done when:

- its acceptance criteria pass;
- tests cover the important behavior;
- no architecture rule above is violated;
- the project still builds from a clean checkout;
- required formatting, lint, and CI-equivalent checks pass;
- no future-slice feature was accidentally pulled into scope.
