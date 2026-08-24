# Thread MCP Notes

## V1 role of MCP

MCP is Thread's first external transport. It is not the core domain model.

Thread core owns:

- tools
- schemas/models
- providers
- capabilities
- execution
- errors

The MCP adapter owns:

- protocol/server lifecycle
- MCP tool discovery mapping
- MCP call mapping
- protocol-specific schema/result/error translation
- HTTP transport details

## Protocol target

The Slice 0 investigation was refreshed immediately before Slice 4 on 2026-08-24.

As of 2026-08-24:

- the current MCP specification is `2026-07-28`;
- that revision moves the core toward stateless request/response semantics;
- Streamable HTTP is the relevant HTTP transport direction;
- legacy HTTP+SSE is deprecated;
- Roots, Sampling, and Logging are deprecated in the core and are unnecessary for Thread V1;
- Thread V1 only needs the tools surface.

## Java SDK compatibility caveat

As of 2026-08-24, the official MCP Java SDK active 2.0.x line has released 2.0.1 but still reports
support for the `2025-11-25` MCP specification, not `2026-07-28`.

Therefore Slice 0 must make an explicit decision rather than assuming the Java SDK is current.

Acceptable V1 strategies include:

### A. Use the official Java SDK behind an adapter

Use it if interoperability with target MCP clients is confirmed for the V1 subset.

Pros:

- less protocol code
- official implementation
- existing Streamable HTTP support
- schema/validation helpers

Cons:

- spec revision lag
- protocol-specific types must be carefully contained

### B. Implement the minimal current MCP tools subset in `transport/mcp`

Only consider this if the official SDK cannot interoperate cleanly with the target clients.

Pros:

- current protocol behavior can be targeted directly
- no wait for SDK release

Cons:

- more protocol/security/testing responsibility
- greater risk of subtle incompatibility

### C. External sidecar bridge

Not preferred for V1 unless JVM transport constraints make embedded MCP unreasonable.

A sidecar could translate MCP <-> a private Thread local API, but it adds packaging and lifecycle complexity for players.

## Selected V1 approach

The released official Java SDK 2.0.1 line was evaluated but is not selected for the V1 runtime. It
implements the `2025-11-25` protocol era, including the initialization/session model that was
removed by `2026-07-28`. A confirmed SDK issue also shows 2.0.0 returning HTTP 500 when an OpenAI
client sends the current `server/discover` request.

Slice 4 therefore implements the minimal `2026-07-28` tools-only Streamable HTTP surface in
`transport.mcp`. The adapter covers `server/discover`, `tools/list`, and `tools/call`, with
JSON-RPC/error translation and required header validation. It will not implement deprecated
HTTP+SSE, the retired initialization/session flow, or unrelated MCP surfaces.

Re-evaluate the SDK before changing the adapter. A stable release with verified `2026-07-28`
interoperability may replace the narrow wire implementation without changing core APIs.

## Slice 4 implementation

- `McpHttpServer` uses the JDK `jdk.httpserver` module; Thread adds no MCP or HTTP runtime library.
- The endpoint is `http://127.0.0.1:25580/mcp` by default and only accepts loopback listener
  options.
- Each request is an independent HTTP POST. There are no sessions, initialization calls, legacy
  GET/SSE endpoints, or server-to-client feature surfaces.
- Requests carrying the retired `Mcp-Session-Id` header are rejected. The modern HTTP cancellation
  signal is closing the in-flight response stream, so Thread does not accept the retired
  `notifications/cancelled` POST.
- `server/discover` advertises only the tools capability and includes Thread name/version metadata.
- `tools/list` is derived directly from deterministic `ToolRegistry` descriptors, including input
  schema, output schema, and read-only annotations.
- `tools/call` invokes only `ToolRegistry.invoke`; transport code imports no Fabric or Minecraft
  types.
- Successful and tool-error results include text content plus structured JSON. Unknown tools and
  malformed protocol requests use JSON-RPC errors.
- The Fabric client entrypoint starts the listener after tool registration and closes it from
  `ClientLifecycleEvents.CLIENT_STOPPING`.
- Unit tests cover HTTP/protocol validation, structured mapping, disconnects, shutdown, and
  same-port restart. The Fabric client game test invokes discovery, status, game info, and inventory
  through the real HTTP listener from menu and loaded-world states.

## Slice 5 hardening

- `config/thread.json` persists listener enablement, explicit loopback host, port, registered tool
  selectors, provider result limits, request size, game-thread deadline, and request concurrency.
- Disabled tools are filtered before registration, so `tools/list`, invocation, and
  `minecraft.get_capabilities` all share the same catalog.
- Configuration cannot opt into a non-loopback listener or exceed Thread's hard safety ceilings.
- A semaphore bounds concurrently handled requests. Excess work receives a controlled HTTP 503;
  accepted work runs on lightweight virtual threads without an unbounded platform-thread pool.
- Client and integrated-server dispatch use the configured deadline. Timed-out futures are
  cancelled and returned as retryable `TIMEOUT` tool errors; lifecycle-time task rejection is a
  retryable `NOT_AVAILABLE` result.
- Transport diagnostics record only status, duration, and unexpected exception class. Request
  bodies, tool arguments, exception messages, inventories, and world results are not logged.

## Transport and security

- bind only to `127.0.0.1`/loopback in V1
- validate browser `Origin` values against loopback hosts
- require the `2026-07-28` protocol and mirrored routing headers
- cap request bodies at the configured value (1 MiB by default, with a fixed 8 MiB hard ceiling)
- bound concurrent requests (8 by default, with a fixed ceiling of 32)
- do not log complete inventories/world results at normal log levels
- validate input schemas before reaching providers
- enforce Thread's own query limits even if protocol/client validation exists

## MCP surface for V1

Expose tools only.

Do not add V1 complexity for:

- MCP Apps
- prompts
- sampling
- roots
- tasks
- elicitation
- remote OAuth flows
- public discovery

Those can be reconsidered when there is a concrete product use case.

## References checked for this plan

- MCP 2026-07-28 release: https://blog.modelcontextprotocol.io/posts/2026-07-28/
- MCP roadmap update, 2026-08-22: https://blog.modelcontextprotocol.io/posts/mcp-roadmap/
- Official Java SDK: https://github.com/modelcontextprotocol/java-sdk
- Java SDK changelog: https://github.com/modelcontextprotocol/java-sdk/blob/main/CHANGELOG.md
- Java SDK 2.0.x/OpenAI discovery incompatibility: https://github.com/modelcontextprotocol/java-sdk/issues/1072
