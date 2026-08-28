# Thread

Thread is a read-only Minecraft context server for AI clients. It exposes structured facts from a
running single-player world through MCP without embedding an AI model or automating gameplay.

The current release targets Minecraft 1.21.11, 26.1.2, and 26.2. Minecraft 1.21.11 uses Java 21; the
26.x lanes use Java 25. Each Minecraft version has its own Fabric, NeoForge, Forge, and universal
artifact; no JAR spans Minecraft versions. Thread is single-player-only and bound to loopback.

Minecraft 1.21.11 is the current minimum supported version. Support for Minecraft 1.21.1 and older
is not a current priority because the intervening Minecraft and loader API changes require a
substantial separate compatibility and parity effort.

## What it exposes

Twenty-one `minecraft.*` tools cover:

- session status and game versions;
- local client options for general, video, audio, controls, accessibility, chat, and bounded
  keybind context, available from menus and multiplayer without reading world state;
- authoritative player vitals, armor/air, effects, movement and condition flags, hotbar/cooldown,
  position, game mode/hardcore, and conditional vehicle/respawn context;
- compact live world context including biome, spawn distance, difficulty/hardcore, time, weather,
  local light, moon phase, and native biome climate values;
- live vanilla advancement lists and detailed criterion progress;
- inventory, held items, and armor;
- the targeted block or entity plus bounded nearby container discovery and inspection;
- bounded nearby loaded entities, with conditional living metadata such as equipment, active
  effects, age, tame ownership, and villager profession;
- live recipe lookup, registry search, and unified live item search across the player and nearby
  loaded containers;
- direct craftability, missing ingredients, and bounded recursive crafting plans;
- the active tool and integration capabilities.

The `tools/list` catalog is self-describing: descriptions distinguish overlapping tools, input
schemas expose defaults and hard limits, and each entry states whether it works in every client
state or requires supported single-player. A Thread-specific model prompt is not required for tool
selection.

Recipes come from Minecraft's live integrated-server recipe manager, so active datapack and mod
recipe additions, replacements, and removals are included. Queries never force-load chunks, scan
the wider world, resolve unopened loot containers, or mutate game state. Nearby storage is exposed
through `minecraft.find_item` as read-only context. Crafting stays player-inventory-only by default,
with an explicit `PLAYER_AND_NEARBY` scope when a client wants eligible loaded containers included.

## Install

1. Install the Java version listed for your Minecraft lane and choose one supported loader:

   | Minecraft | Java | Fabric | NeoForge | Forge |
   | --- | --- | --- | --- | --- |
   | 1.21.11 | 21 | Loader 0.19.3 + Fabric API 0.141.6+1.21.11 | 21.11.45 | 61.2.1 |
   | 26.1.2 | 25 | Loader 0.19.3 + Fabric API 0.154.0+26.1.2 | 26.1.2.41-beta | 64.0.12 |
   | 26.2 | 25 | Loader 0.19.3 + Fabric API 0.154.0+26.2 | 26.2.0.62 | 65.1.2 |

2. Put the matching `thread-universal-<minecraft>-<thread-version>.jar` in the instance's `mods`
   folder. It
   works on all three loaders for that exact Minecraft version. Matching dedicated
   `thread-fabric-<minecraft>-<thread-version>.jar`,
   `thread-neoforge-<minecraft>-<thread-version>.jar`, and
   `thread-forge-<minecraft>-<thread-version>.jar` builds remain available for modpacks and
   troubleshooting. Install exactly one Thread JAR.
3. Launch Minecraft. Thread starts its MCP server automatically.

## Connect an MCP client

The default Streamable HTTP endpoint is `http://127.0.0.1:25580/mcp`. For Codex:

```toml
[mcp_servers.minecraft]
enabled = true
url = "http://127.0.0.1:25580/mcp"
```

Restart Codex and open a new task after changing its MCP configuration. Minecraft and Thread must
be running before the client connects. Thread supports standard `initialize` discovery used by
Codex and the MCP `2026-07-28` stateless discovery flow; neither creates a server-side session.

## Configuration

Thread creates `config/thread.json` on first launch:

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

Every field except `schemaVersion` may be omitted to keep its safe default. Tool selectors accept
exact IDs and namespace wildcards. Disabled tools are absent from discovery. Integration IDs in
`disabledIntegrations` are skipped before their implementation is loaded. Listener hosts are
restricted to `127.0.0.1`, `localhost`, or `::1`, and configurable limits remain below fixed hard
ceilings. Invalid existing files are preserved for correction while that launch uses safe defaults.
Configuration is loaded when Minecraft starts; restart the game after changing this file.

Set `mcpEnabled` to `false` to initialize Thread without opening the HTTP listener.

## Troubleshooting

- **The MCP client cannot connect:** Confirm Minecraft is still running, Thread logged its listener
  address, and the client URL matches `mcpBindHost` and `mcpPort` in the instance's
  `config/thread.json`.
- **The listener could not start:** Another process may own the port. Choose an unused loopback
  port, update the MCP client URL, and restart Minecraft.
- **Tools disappear:** Check `enabledTools`. Disabled tools are intentionally omitted from
  discovery, while all gameplay tools are unavailable outside an integrated single-player world.
- **The configuration is rejected:** Thread preserves the invalid file and starts with safe
  defaults. Correct the field named in the Minecraft log, then restart the game.
- **Minecraft reports incompatible mods:** Use the exact Minecraft, Java, and selected-loader
  versions listed under Install. Fabric requires Fabric API; NeoForge and Forge do not. Make sure
  the `mods` folder contains only one universal or dedicated Thread JAR.
- **No listener is expected:** `mcpEnabled: false` keeps the tools initialized inside Thread but
  intentionally does not open an HTTP endpoint.

## Thread Integrations

The base artifact includes no third-party gameplay-mod or recipe-viewer adapter. Future,
separately distributed integration mods can use the metadata-only `thread:integrations` Fabric
entrypoint or NeoForge/Forge Java service provider and Thread's public transactional contribution
contracts. Target-mod types remain outside Thread core and MCP.

See [Thread Integrations](docs/INTEGRATIONS.md) for the supported API and packaging boundary.

## Current boundaries

- No Minecraft 1.21.1 or older support; 1.21.11 is the current minimum.
- No multiplayer gameplay queries or dedicated-server mode.
- No remote binding, authentication, or public MCP hosting.
- No commands, movement, crafting actions, inventory changes, or world edits.
- No automatic item movement or implicit use of nearby storage in crafting calculations.
- No raw NBT/components or Minecraft objects in public core contracts.
- No bundled JEI, EMI, REI, FTB Quests, Create, AE2, Mekanism, or similar adapter.
- No separately released Thread Integrations package yet; those remain future work.
- Crafting plans are deterministic and bounded, not exhaustive global optimizers.

`minecraft.get_status`, `minecraft.get_game_info`, `minecraft.get_client_options`,
`minecraft.search_items`, and `minecraft.get_capabilities` remain available in menus, loading
states, single-player, and unsupported multiplayer. Client options and registry search are local
client context only; all gameplay-state tools require a supported integrated single-player
session.

## Development

The Gradle build separates version-neutral `:common` code, shared Minecraft-facing sources, small
`:minecraft:1.21.11`, `:minecraft:26.1.2`, and `:minecraft:26.2` API bindings, and nested
Fabric/NeoForge/Forge projects.
Each version lane compiles the same justified shared sources against exactly one Minecraft version,
then a root packaging task produces its universal JAR. Releases contain three independent
four-artifact matrices. All three lanes expose the same twenty-one-tool capability set.

Use the Gradle Wrapper. The normal local gate is:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean spotlessCheck check build
```

Module tasks and outputs are documented in [Build](docs/BUILD.md). Packaged-client coverage is in
[Development](docs/DEVELOPMENT.md), and the artifact/publishing gate is in
[Release](docs/RELEASE.md).

Contributor references:

- [Current scope](docs/SCOPE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Build](docs/BUILD.md)
- [Tool contracts](docs/TOOL_CONTRACTS.md)
- [Development and testing](docs/DEVELOPMENT.md)
- [Release](docs/RELEASE.md)
- [Thread Integrations](docs/INTEGRATIONS.md)
- [MCP transport notes](docs/MCP_NOTES.md)
- [Current decisions](docs/DECISIONS.md)
- [Roadmap](docs/ROADMAP.md)
- [Agent instructions](AGENTS.md)
