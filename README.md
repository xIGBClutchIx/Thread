# Thread

Thread is a read-only Minecraft context server for AI clients. It exposes structured facts from a
running single-player world through MCP without embedding an AI model or automating gameplay.

Thread 0.1.0 targets Minecraft 26.2 and Java 25 on Fabric Loader 0.19.3 with Fabric API
0.154.0+26.2, NeoForge 26.2.0.62, or Forge 65.1.2. V1 is single-player-only and bound to loopback.

## What it exposes

Fifteen `minecraft.*` tools cover:

- session status and game versions;
- player health, hunger, experience, position, dimension, and game mode;
- inventory, held items, and armor;
- the targeted block plus bounded nearby container discovery and inspection;
- bounded nearby loaded entities;
- live recipe lookup and item search;
- direct craftability, missing ingredients, and bounded recursive crafting plans;
- the active tool and integration capabilities.

Recipes come from Minecraft's live integrated-server recipe manager, so active datapack and mod
recipe additions, replacements, and removals are included. Queries never force-load chunks, scan
the wider world, resolve unopened loot containers, or mutate game state. Nearby storage is exposed
only as read-only context; crafting calculations still use the player's main inventory alone.

## Install

1. Install Minecraft 26.2 with Java 25 using Fabric Loader 0.19.3 and Fabric API 0.154.0+26.2,
   NeoForge 26.2.0.62, or Forge 65.1.2.
2. Put `thread-universal-0.1.0.jar` in your instance's `mods` folder. It is the recommended download
   and works on all three supported loaders. Dedicated `thread-fabric-0.1.0.jar`,
   `thread-neoforge-0.1.0.jar`, and `thread-forge-0.1.0.jar` builds remain available for modpacks,
   compatibility testing, and troubleshooting. Install exactly one Thread JAR.
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

The base artifact includes no third-party gameplay-mod or recipe-viewer adapter. Separate optional
mods can use the metadata-only `thread:integrations` Fabric entrypoint or NeoForge/Forge Java service
provider and Thread's public, transactional contribution contracts. Target-mod types remain
outside Thread core and MCP.

See [Thread Integrations](docs/INTEGRATIONS.md) for the supported API and packaging boundary.

## V1 boundaries

- No multiplayer gameplay queries or dedicated-server mode.
- No remote binding, authentication, or public MCP hosting.
- No commands, movement, crafting actions, inventory changes, or world edits.
- No automatic item movement or use of nearby storage in crafting calculations.
- No raw NBT/components or Minecraft objects in public core contracts.
- No bundled JEI, EMI, REI, FTB Quests, Create, AE2, Mekanism, or similar adapter.
- Crafting plans are deterministic and bounded, not exhaustive global optimizers.

`minecraft.get_status` remains available in menus, loading states, single-player, and unsupported
multiplayer. Other gameplay tools require a supported integrated single-player session.

## Development

The Gradle build has a `:common` module for core/MCP/shared Minecraft behavior, thin `:fabric`,
`:neoforge`, and `:forge` adapters, and a packaging-only `:universal` module. Releases contain the
recommended universal JAR plus all three dedicated loader JARs.

Use the Gradle Wrapper. The normal local gate is:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean spotlessCheck check build
```

Module tasks and outputs are documented in [Build](docs/BUILD.md). Packaged-client coverage is in
[Development](docs/DEVELOPMENT.md), and the artifact/publishing gate is in
[Release](docs/RELEASE.md).

Contributor references:

- [V1 scope](docs/V1_SCOPE.md)
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
