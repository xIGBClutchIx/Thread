# Thread V1 Scope

## Product statement

Thread V1 is a read-only Minecraft context server that exposes a small set of live game tools to MCP clients.

Thread is not an AI model, chatbot, autonomous player, or modpack guide by itself. It provides structured Minecraft facts that an external model can request when useful.

## Primary user story

As a Minecraft player, I can install Thread and connect an MCP-capable AI client so the client can answer questions using my actual running game state rather than generic Minecraft knowledge alone.

## V1 target

- Minecraft Java Edition
- Fabric only
- Java implementation
- Minecraft 26.2 with Fabric Loader 0.19.3 and Fabric API 0.154.0+26.2
- Local MCP access only by default
- Read-only tools only
- Java formatting/linting/tests enforced by Gradle and GitHub Actions

These versions were selected and pinned during Slice 0. V1 does not attempt multi-version
compatibility. Minecraft 26.2 requires Java 25, which is also the project toolchain.

## In scope

### Core framework

- stable Thread DTOs
- provider interfaces
- tool registry
- context registry
- integration registry
- optional-mod candidate discovery with absent-mod-safe class loading
- transactional tool/context/recipe/enrichment contributions
- structured errors
- capability discovery
- explicit limits

### Live Minecraft reads

- runtime/session status, including menu vs loaded world
- game/version information
- player status
- player inventory
- equipped items
- current dimension and position
- block currently targeted by the player
- nearby loaded entities within a bounded radius
- vanilla recipe lookup
- deterministic inventory-to-recipe craftability and missing-ingredient assessment
- deterministic recursive crafting plans with bounded cycle/depth handling
- vanilla item search

### Engineering baseline

- meaningful comments for non-obvious implementation decisions
- Javadocs on public Thread contracts/extension points
- Spotless formatting
- Checkstyle lint/style checks
- `.editorconfig`
- GitHub Actions CI for pushes/pull requests/manual runs
- tag-driven validated release build by V1 release readiness

### MCP

- MCP server lifecycle
- tool discovery
- tool invocation
- JSON schema/input validation
- structured results
- structured errors
- loopback-only default binding
- clean shutdown

## Explicitly out of scope

- NeoForge
- Forge
- Quilt
- multiple Minecraft versions
- custom in-game AI/chat screen
- direct OpenAI API integration
- autonomous actions
- inventory modification
- crafting actions
- movement
- block placement/breaking
- command execution
- remote/public MCP hosting
- account/authentication systems
- JEI/REI/EMI integration
- FTB Quests integration
- Create/Mekanism/AE2/etc. integrations
- broad chest/container scanning
- world-wide searches
- chunk generation/loading for queries
- semantic embeddings/vector databases
- long-term player memory
- voice input/output

V1 includes the integration framework and a test-only proof integration. It still ships no JEI,
EMI, REI, FTB Quests, Create, Mekanism, storage-network, or other substantial third-party support.

## V1 success criteria

From a clean install, an MCP client can discover Thread and correctly answer each of these using live tool calls:

1. "Am I currently in a playable single-player world?"
2. "What version of Minecraft am I playing?"
3. "How much health and hunger do I have?"
4. "What is in my inventory?"
5. "What am I holding/wearing?"
6. "What block am I looking at?"
7. "What entities are near me?"
8. "How do I craft a diamond pickaxe?"
9. "Do I have the materials for a diamond pickaxe?"
10. "What intermediate crafts and raw materials do I need for a crafting table?"

For #9, Thread performs a deterministic comparison against the player's current main inventory and
reports each recipe variant independently with required, allocated, and missing counts. For #10,
Thread recursively plans intermediate recipes with one shared inventory ledger and reports final raw
shortages plus structured cycle/depth/work-limit issues. Both are read-only analyses: Thread does not
craft items, search nearby storage, model workstation/fuel feasibility, or globally optimize every
recipe combination.

## Non-goals that protect the architecture

V1 does not need to prove every future feature. It needs to prove that:

- live Minecraft state can be represented cleanly;
- tools can be added without coupling them to MCP;
- an external agent can discover and call those tools;
- future mod integrations have an obvious extension point;
- the core can be tested independently of Minecraft.
