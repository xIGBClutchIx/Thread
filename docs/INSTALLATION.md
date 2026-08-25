# Installing Thread V1

Thread is a client-side Fabric mod. A release contains one installable runtime JAR and its SHA-256
checksum; source and game-test JARs are not installable artifacts.

## Requirements

| Component | Required version |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | 0.154.0+26.2 or newer for Minecraft 26.2 |
| Thread | 0.1.0 |

Fabric API versions for a different Minecraft release are incompatible even when their numeric
version is newer.

## Install the release

1. Create or select a Fabric client instance with the versions above.
2. Download `thread-0.1.0.jar` and `thread-0.1.0.jar.sha256` from the `v0.1.0` release.
3. Verify the checksum.
4. Copy `thread-0.1.0.jar` into the instance's `mods` folder beside Fabric API.
5. Start the client. No second Thread component, library, or server mod is required.

PowerShell:

```powershell
$actual = (Get-FileHash .\thread-0.1.0.jar -Algorithm SHA256).Hash.ToLower()
$expected = (Get-Content .\thread-0.1.0.jar.sha256).Split(' ')[0]
if ($actual -ne $expected) { throw "Thread checksum mismatch" }
```

Linux or macOS:

```bash
sha256sum --check thread-0.1.0.jar.sha256
```

On macOS, use `shasum -a 256 thread-0.1.0.jar` if `sha256sum` is unavailable and compare the output
with the checksum file.

## Connect Codex

Add this section to the Codex user configuration:

```toml
[mcp_servers.minecraft]
enabled = true
url = "http://127.0.0.1:25580/mcp"
```

Restart Codex and create a new task so it attaches the newly configured server. Start Minecraft
with Thread before using the tools. The endpoint supports the Codex Streamable HTTP sequence
`initialize` -> `notifications/initialized` -> `tools/list`/`tools/call`, as well as independent MCP
`2026-07-28` `server/discover` requests. It does not create protocol sessions.

## Confirm the installation

The Minecraft log should contain both:

```text
Thread 0.1.0 initialized for Minecraft 26.2 with Fabric Loader 0.19.3
Thread MCP listener started at http://127.0.0.1:25580/mcp
```

An MCP client should discover twelve read-only tools. Call `minecraft.get_status` from the main menu;
it should report `MAIN_MENU`, `worldLoaded: false`, and `supported: false`. Enter a single-player
world and call it again; it should report `SINGLEPLAYER` and `supported: true`.

## Configuration and troubleshooting

Thread creates `config/thread.json` in the Minecraft instance directory on first launch. The
complete schema and defaults are in the [README](../README.md#configuration).

- If the listener log is absent, confirm `mcpEnabled` is `true`.
- If binding fails, another process may own the configured port. Stop it or change `mcpPort` and
  update the client URL.
- Thread accepts only `127.0.0.1`, `localhost`, or `::1`. Remote binding is not supported.
- If an existing configuration is invalid, Thread preserves it, logs a validation error without
  printing its contents, and uses safe defaults for that launch. Correct the file and restart.
- If a gameplay call fails outside a world or in multiplayer, call `minecraft.get_status` first.
  Multiplayer gameplay access is intentionally unsupported in V1.
- If a tool is absent, inspect `enabledTools`. An empty array exposes no tools.
- If Fabric reports an incompatible dependency, make sure every mod targets Minecraft 26.2 and
  remove duplicate Fabric API JARs from the instance.

To disable the network endpoint, set `"mcpEnabled": false`. Thread still loads normally and opens
no MCP listener.
