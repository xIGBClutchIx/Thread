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
multiple loaders, or multiple Minecraft versions until those products have their own trust and
compatibility designs.

## Boundaries are more important than portability frameworks

Core DTOs, providers, registries, and services contain no Minecraft, Fabric, MCP, raw NBT, generic
component map, or optional-mod type. Fabric converts live game objects into detached DTOs. MCP maps
only the tool registry. The project leaves room for another adapter without prebuilding one.

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

Spotless, Checkstyle, compiler checks, unit/architecture tests, release-artifact inspection, and two
packaged-client runs are mandatory. The normal packaged test uses the remapped runtime JAR in a real
temporary single-player world; the second proves MCP-disabled startup. A release is not validated by
compilation alone.
