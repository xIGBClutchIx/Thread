# Thread MCP Transport

MCP is Thread's first external transport, not its domain model. The adapter lives in `:common`,
maps JSON-RPC requests to `ToolRegistry`, and has no Minecraft or loader dependency.

## Selected implementation

Thread implements its narrow tools-only Streamable HTTP surface with the JDK `jdk.httpserver`
module and Minecraft-provided Gson. It has no MCP SDK or additional HTTP runtime dependency.

The default endpoint is `http://127.0.0.1:25580/mcp`. Every message is an independent HTTP POST;
Thread creates no protocol session, never emits or requires `Mcp-Session-Id`, and exposes no GET/SSE
stream endpoint.

The same endpoint supports two interoperable openings:

### Initialization flow

1. `initialize` with `params.protocolVersion`, `params.capabilities`, and `params.clientInfo`;
2. Thread returns the negotiated version, server identity, tools capability, and instructions;
3. `notifications/initialized` returns HTTP 202;
4. later `tools/list` and `tools/call` requests include `MCP-Protocol-Version`.

This is the standard Streamable HTTP flow used by Codex. `initialize` does not require the protocol
header because it negotiates that value. Thread accepts `2026-07-28` and the initialization-era
`2025-11-25` value used by compatible clients.

### Stateless discovery flow

Clients that implement MCP `2026-07-28` stateless discovery can call `server/discover`, then
`tools/list` and `tools/call`, with `MCP-Protocol-Version: 2026-07-28` on each request.

Discovery returns the tools capability, instructions, supported versions, a 60-second TTL, public
cache scope, and server identity under `result._meta.io.modelcontextprotocol/serverInfo`.
`tools/list` returns the same server metadata and cache hints.

Thread does not require custom `Mcp-Method`, `Mcp-Name`, or per-request `_meta` mirrors. Those older
Thread-specific validation experiments are not part of the transport contract.

## Tool mapping

- `tools/list` is generated from stable `ToolRegistry` descriptors.
- Each descriptor includes its input/output schemas and read-only, non-destructive, idempotent,
  closed-world annotations.
- `tools/call` accepts a registered name and an object-valued `arguments` field.
- Input is validated by the core codec before execution; serialized output is checked against the
  declared output schema.
- Successes and tool failures include both JSON text content and `structuredContent`.
- Tool failures stay structured Thread errors; malformed JSON-RPC, protocol, and unknown-tool
  requests use JSON-RPC errors.

MCP never contains crafting, session, or Minecraft query logic. It invokes only the registry.

## HTTP and security rules

- Bind addresses must be explicit IPv4/IPv6 loopback values.
- Only `POST /mcp` is accepted.
- Browser `Origin`, when present, must be HTTP(S) loopback with no credentials, path, query, or
  fragment.
- `Content-Type` must be `application/json`.
- `Accept` must include both `application/json` and `text/event-stream`, as required by Streamable
  HTTP negotiation, even though Thread returns JSON for its request/response-only surface.
- Request bodies are capped at the configured size: 1 MiB by default and 8 MiB hard maximum.
- Concurrent requests are bounded: 8 by default and 32 hard maximum. Excess work receives HTTP 503.
- Provider dispatch uses the configured game-thread deadline and returns structured timeout or
  lifecycle errors.
- Logs contain only response status, duration, and unexpected exception class. They never include
  request bodies, tool arguments, inventories, world results, or exception messages.
- Responses set `X-Content-Type-Options: nosniff`.

Configuration may disable MCP or choose another loopback port, but it cannot enable remote binding
or exceed hard ceilings.

## Lifecycle

The shared `ThreadRuntime` starts MCP only after vanilla and external integration registration has
produced the final tool registry. Each loader entrypoint requests that start and logs a bind
failure without taking down Minecraft. Loader shutdown events close the shared runtime and
listener; tests verify clean shutdown and same-port restart on all three loaders.

## Tests that protect compatibility

The real local HTTP tests cover:

- `initialize` -> `notifications/initialized` -> `tools/list`;
- stateless `server/discover` -> `tools/list` -> `tools/call`;
- protocol negotiation and required post-initialization headers;
- request envelope, JSON, content negotiation, origin, size, and concurrency failures;
- tool schemas, annotations, structured success/error mapping, disconnects, and shutdown.

Packaged client tests repeat initialization against each actual loader-specific JAR. A shared
loader-neutral parity fixture verifies the exact catalog, exercises all thirteen tool paths across
the menu/world/menu lifecycle, and repeats representative status, player, recipe, and crafting
calls through MCP on Fabric, NeoForge, and Forge.

## Deliberately absent MCP features

V1 exposes tools only. It does not implement MCP Apps, prompts, roots, sampling, tasks, elicitation,
logging, OAuth, remote discovery, server-to-client requests, protocol sessions, or legacy HTTP+SSE.
Add one only for a concrete product requirement and keep it behind the transport boundary.
