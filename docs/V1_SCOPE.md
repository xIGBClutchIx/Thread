# Thread V1 Scope

## Product statement

Thread V1 is a read-only Minecraft context server that exposes a small set of live game tools to MCP clients.

Thread is not an AI model, chatbot, autonomous player, or modpack guide by itself. It provides structured Minecraft facts that an external model can request when useful.

## Primary user story

As a Minecraft player, I can install Thread and connect an MCP-capable AI client so the client can answer questions using my actual running game state rather than generic Minecraft knowledge alone.

## V1 target

- Minecraft Java Edition
- Fabric, NeoForge, and Forge client loaders
- Java implementation
- Minecraft 26.1.2 with Fabric Loader 0.19.3 plus Fabric API 0.154.0+26.1.2,
  NeoForge 26.1.2.41-beta, or Forge 64.0.12
- Minecraft 26.2 with Fabric Loader 0.19.3 plus Fabric API 0.154.0+26.2,
  NeoForge 26.2.0.62, or Forge 65.1.2
- Local MCP access only by default
- Read-only tools only
- Java formatting/linting/tests enforced by Gradle and GitHub Actions

V1 ships separate 26.1.2 and 26.2 implementations and artifacts. It never selects or branches
across versions at runtime. Both require Java 25, which is also the project toolchain.

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
- bounded local client settings grouped into general, video, audio, controls, accessibility, chat,
  and opt-in keybind sections, without requiring a world
- authoritative player vitals, armor/air, active effects, movement and condition flags, selected
  hotbar/cooldown state, game mode/hardcore, and conditional vehicle/respawn context
- player inventory
- equipped items
- live vanilla advancements known to the player, including criterion progress, completion state,
  hierarchy, display metadata, and available timestamps
- current dimension and position
- compact current world/environment context, including biome, global spawn distance,
  difficulty/hardcore, day/time, daylight, weather, local light, moon phase, and native biome
  climate values
- block currently targeted by the player
- entity currently targeted by the player's normal client crosshair, resolved from authoritative
  loaded server state with bounded living metadata
- nearby loaded entities within a bounded radius, using the same entity shape
- nearby container discovery and individual loaded-container inspection within a stricter bounded
  radius, including common vanilla storage and processing block entities
- live native recipe lookup, including datapack and installed-mod changes
- deterministic inventory-to-recipe craftability and missing-ingredient assessment, optionally
  including eligible nearby loaded containers when explicitly requested
- deterministic recursive crafting plans with bounded cycle/depth handling and the same explicit
  item-source scope
- vanilla registry item search
- unified live item search across player inventory, offhand/armor, and nearby loaded containers,
  with aggregate counts and structured source locations

### Engineering baseline

- Gradle `:common`, `minecraft/shared`, isolated `:minecraft:26.1.2` and `:minecraft:26.2`
  bindings, nested thin loader projects, and matrix-driven universal packaging tasks
- a version-specific universal JAR and independently installable dedicated JAR for every supported
  Minecraft/loader combination
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

- Quilt
- runtime cross-version compatibility or one JAR spanning Minecraft versions
- custom in-game AI/chat screen
- direct OpenAI API integration
- autonomous actions
- inventory modification
- client setting mutation
- crafting actions
- movement
- block placement/breaking
- command execution
- remote/public MCP hosting
- account/authentication systems
- JEI/REI/EMI integration in the base artifact
- FTB Quests integration
- Create/Mekanism/AE2/etc. integrations
- unbounded or world-wide chest/container scanning
- world-wide searches
- chunk generation/loading for queries
- semantic embeddings/vector databases
- long-term player memory
- voice input/output

V1 includes the generic integration framework but ships no third-party gameplay-mod or
recipe-viewer adapter. Future JEI, FTB Quests, Create, AE2, Mekanism, storage-network, and similar
support belongs in separately distributed **Thread Integrations** packages.

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
11. "What loaded containers are near me?"
12. "What is inside that nearby furnace or chest?"
13. "Where are my coal and diamonds across my inventory and nearby loaded containers?"
14. "Which vanilla advancements have I completed, and what criteria remain for one of them?"
15. "What biome and dimension am I in, how far am I from world spawn, and what are the current
    time, weather, light, difficulty, and hardcore state?"
16. "What entity am I looking at, what is it carrying, and what useful vanilla state does it have?"
17. "What are my current video, audio, accessibility, chat, control, and requested keybind
    settings?"

For #9, Thread performs a deterministic comparison and reports each recipe variant independently
with required, allocated, missing, and live-source counts. For #10, Thread recursively plans
intermediate recipes with one shared item ledger and reports final raw shortages plus structured
cycle/depth/work-limit issues. Both default to the player's 36-slot main inventory. A client may
explicitly select `PLAYER_AND_NEARBY` to add eligible containers from one bounded, loaded-world
snapshot; unresolved loot and containers whose contents cannot be represented safely are excluded,
and incomplete discovery is visible in the result. These remain read-only analyses: Thread does not
craft or move items, include equipment, model workstation/fuel feasibility, or globally optimize
every recipe combination. The base recipe source is Minecraft's final live recipe manager, not a
static vanilla list, so supported datapack and installed-mod recipe changes flow through the same
tool contracts.

## Non-goals that protect the architecture

V1 does not need to prove every future feature. It needs to prove that:

- live Minecraft state can be represented cleanly;
- tools can be added without coupling them to MCP;
- an external agent can discover and call those tools;
- future mod integrations have an obvious extension point;
- the core can be tested independently of Minecraft.
