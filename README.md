# Thread

Thread is a read-only Minecraft context server for AI clients. It exposes structured facts from a
running single-player world through MCP without embedding an AI model or automating gameplay.

Thread 0.1.0 targets Minecraft 26.2, Java 25, Fabric Loader 0.19.3, and Fabric API
0.154.0+26.2. V1 is Fabric-only, single-player-only, and bound to loopback.

## What it exposes

Thirteen `minecraft.*` tools cover:

- session status and game versions;
- player health, hunger, experience, position, dimension, and game mode;
- inventory, held items, and armor;
- the targeted block and safe container contents;
- bounded nearby loaded entities;
- live recipe lookup and item search;
- direct craftability, missing ingredients, and bounded recursive crafting plans;
- the active tool and integration capabilities.

Recipes come from Minecraft's live integrated-server recipe manager, so active datapack and Fabric
mod recipe additions, replacements, and removals are included. Queries never force-load chunks,
scan the wider world, use nearby storage, or mutate game state.

## Install

1. Install Minecraft 26.2 with Java 25, Fabric Loader 0.19.3, and Fabric API 0.154.0+26.2.
2. Download `thread-0.1.0.jar` and its matching `.sha256` file.
3. Verify the checksum and copy only the JAR into the instance's `mods` directory.
4. Launch Minecraft and confirm the log reports both Thread initialization and the MCP listener.

PowerShell checksum verification:

```powershell
(Get-FileHash .\thread-0.1.0.jar -Algorithm SHA256).Hash.ToLower()
Get-Content .\thread-0.1.0.jar.sha256
```

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
exact IDs and namespace wildcards. Disabled tools are absent from discovery. Listener hosts are
restricted to `127.0.0.1`, `localhost`, or `::1`, and configurable limits remain below fixed hard
ceilings. Invalid existing files are preserved for correction while that launch uses safe defaults.

Set `mcpEnabled` to `false` to initialize Thread without opening the HTTP listener.

## Thread Integrations

The base artifact includes no third-party gameplay-mod or recipe-viewer adapter. Separate optional
mods can use the metadata-only `thread:integrations` Fabric entrypoint and Thread's public,
transactional contribution contracts. Target-mod types remain outside Thread core and MCP.

See [Thread Integrations](docs/INTEGRATIONS.md) for the supported API and packaging boundary.

## V1 boundaries

- No multiplayer gameplay queries or dedicated-server mode.
- No remote binding, authentication, or public MCP hosting.
- No commands, movement, crafting actions, inventory changes, or world edits.
- No raw NBT/components or Minecraft objects in public core contracts.
- No bundled JEI, EMI, REI, FTB Quests, Create, AE2, Mekanism, or similar adapter.
- Crafting plans are deterministic and bounded, not exhaustive global optimizers.

`minecraft.get_status` remains available in menus, loading states, single-player, and unsupported
multiplayer. Other gameplay tools require a supported integrated single-player session.

## Development

Use the Gradle Wrapper. The normal local gate is:

```powershell
.\gradlew.bat spotlessApply
.\gradlew.bat clean spotlessCheck check build
```

Packaged-client and release checks are documented in [Development](docs/DEVELOPMENT.md).

Contributor references:

- [V1 scope](docs/V1_SCOPE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Tool contracts](docs/TOOL_CONTRACTS.md)
- [Development, testing, and release](docs/DEVELOPMENT.md)
- [Thread Integrations](docs/INTEGRATIONS.md)
- [MCP transport notes](docs/MCP_NOTES.md)
- [Current decisions](docs/DECISIONS.md)
- [Roadmap](docs/ROADMAP.md)
- [Agent instructions](AGENTS.md)
