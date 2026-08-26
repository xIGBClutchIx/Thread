# Thread V1 Decisions

This file records current architectural choices that are easy to accidentally undo. Historical
implementation steps belong in Git, not here.

## Thread is a context layer

Thread exposes structured live Minecraft facts to external clients. It does not embed an AI model,
provider SDK, autonomous player, or custom assistant UI. MCP is the first transport over core tools,
not the core API.

## V1 is deliberately narrow

V1 is Java 25, Fabric client, Minecraft 26.2, single-player, read-only, bounded, and loopback-only.
Gameplay tools reject multiplayer before exposing state. `minecraft.get_status` remains available
from every client state as the safe preflight.

Thread does not implement remote access, authentication, actions, dedicated-server behavior,
another loader, or multiple Minecraft versions until those products have their own trust and
compatibility designs.

## Common and Fabric are separate modules without a portability framework

`:common` owns core, configuration, MCP, runtime assembly, and Minecraft-facing code that can run
unchanged when invoked by another client loader. `:fabric` owns only Fabric entrypoint, Loader API,
lifecycle, config-path, version-predicate, and integration-entrypoint wiring. The Fabric release JAR
merges common classes; common is not installed or published independently.

Core DTOs, providers, registries, and services contain no Minecraft, loader, MCP, raw NBT, generic
component map, or optional-mod type. Shared Minecraft adapters convert live game objects into
detached DTOs. MCP maps only the tool registry. Common source has no Fabric imports or runtime
dependencies.

Thread does not use Architectury or a custom platform god object, and it does not wrap every
Minecraft class. Config-directory and lifecycle abstractions are intentionally absent because the
thin Fabric entrypoint consumes those values directly. Add another contract only when a second
loader proves a real difference.

Java type documentation belongs on public types and architecture documentation belongs under
`docs/`; `package-info.java` is not used.

## Versions and dependencies are pinned

The V1 baseline is Minecraft 26.2, Java 25, Fabric Loader 0.19.3, Fabric API 0.154.0+26.2, Loom
1.17.19, Gradle 9.5.1, Spotless 8.10.0, google-java-format 1.36.0, Checkstyle 14.0.0, and JUnit
6.1.2.

Minecraft supplies Gson 2.14.0. Thread uses it behind explicit Thread-owned JSON schemas rather
than bundling another JSON library or generating schemas through reflection.

## Live native state is authoritative

Providers pull bounded snapshots on the correct Minecraft logical thread. World queries inspect
only already-loaded state and never force-load chunks.

Recipes come from the integrated server's final live `RecipeManager`, including active datapack and
Fabric-mod changes. Thread has no static vanilla recipe catalog.

## Crafting intelligence remains deterministic and bounded

Direct craftability uses maximum-flow allocation so overlapping alternatives cannot spend the same
inventory unit twice. Recursive planning uses one shared inventory/crafted-surplus ledger,
active-path cycle detection, deterministic local variant scoring, and hard depth/work/quantity
limits.

This complexity is retained because greedy allocation, global visited sets, or unbounded recursion
produce incorrect or unsafe results. Planning does not inspect nearby storage, model stations/fuel,
globally optimize all combinations, or craft items.

## Optional integrations load metadata first

Third-party adapters ship as separate Thread Integrations JARs. Their Fabric entrypoint returns only
`IntegrationCandidate` metadata. Thread applies enabled, mod-presence, and version checks before
`ReflectiveIntegrationLoader` resolves the implementation class name.

Reflection is retained specifically to prevent absent optional APIs from linking early. Eager
`ServiceLoader`, implementation imports, and class literals would break that guarantee.

Registration is transactional and contribution failures are isolated. Base Thread registers only
the built-in vanilla integration.

## Native recipes remain the guarded fallback

The composite recipe provider captures the native result first so session rejection and query
limits cannot be bypassed. The first successful non-empty external provider in stable integration-ID
order may replace that result for one item. Empty or failed contributions preserve the native
result unchanged.

## MCP supports both current client openings without sessions

Thread's JDK HTTP adapter supports standard `initialize`/`notifications/initialized` clients and
MCP `2026-07-28` stateless `server/discover` clients on the same POST endpoint. Both use the same
`tools/list` and `tools/call` implementation.

Initialization is compatibility negotiation, not stored session state. Thread neither emits nor
requires `Mcp-Session-Id`, custom mirrored method/name headers, or per-request protocol metadata.
Legacy GET/SSE and non-tool MCP feature surfaces are absent.

## Quality and release proofs are part of V1

Spotless, Checkstyle, compiler checks, unit/architecture tests, release-artifact inspection, and
three packaged-client runs are mandatory. The normal packaged test uses the merged Fabric runtime
JAR in a real temporary single-player world; the restart run proves persisted configuration reload,
and the final run proves MCP-disabled startup. A release is not validated by compilation alone.
