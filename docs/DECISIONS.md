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
- current Streamable HTTP requires per-request protocol metadata and routing headers;
- the official Java SDK 2.0.1 line still targets `2025-11-25` and its earlier
  initialization/session model;
- a confirmed SDK issue shows 2.0.0 failing current OpenAI `server/discover` requests with HTTP 500.

Thread does not build V1 code around the obsolete protocol lifecycle. Slice 4 implements
the minimal current tools-only surface inside `transport.mcp`: `server/discover`, `tools/list`, and
`tools/call`, plus protocol/header validation and structured error mapping. Legacy HTTP+SSE and the
retired initialization/session flow will not be implemented.

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
