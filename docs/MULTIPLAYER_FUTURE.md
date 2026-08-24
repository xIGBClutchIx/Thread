# Thread Multiplayer Future Notes

This document intentionally does not define V1 implementation work. It records the questions that must be answered before Thread supports multiplayer or dedicated servers.

## Why multiplayer is deferred

Read-only does not automatically mean harmless in multiplayer. Thread could expose information that a server owner, modpack, or anti-cheat system expects a player not to have programmatic access to. Multiplayer also introduces multiple authorities and multiple users instead of one local player controlling both client and integrated server.

V1 therefore supports integrated single-player worlds only.

## Product modes to decide later

Multiplayer support may need more than one mode:

1. **Client-only multiplayer**: Thread is installed only on the player's client and may access only information the client legitimately receives.
2. **Server-enabled multiplayer**: Thread is installed on both client and server and the server explicitly advertises/authorizes additional capabilities.
3. **Dedicated server / admin mode**: Thread runs server-side for administration, diagnostics, support, or automation rather than as a player assistant.

Do not assume these modes should expose the same tools.

## Trust and authority questions

Before multiplayer implementation, decide:

- Which side is authoritative for each provider?
- Can a client use Thread on a server that does not have Thread installed?
- Should servers be able to disable Thread entirely?
- How does a server advertise allowed capabilities?
- Are permissions per server, per player, per role, or per tool?
- Does a server need to approve the external MCP client as well as the Minecraft player?
- What happens when client and server Thread versions/capabilities differ?

## Cheating and fair-play questions

Potentially sensitive examples include:

- entities outside normal player awareness
- block/entity data through walls
- container contents the player has not opened
- hidden recipe/progression data
- other-player inventory/equipment/status
- exact coordinates or metadata a normal UI hides
- server-side quest/objective state
- automation or actions based on information faster than a human could inspect it

Each multiplayer tool needs an explicit visibility rule. "The client technically has the packet" should not automatically mean "Thread should expose it."

## Privacy questions

Multiplayer state can belong to other people. Decide whether Thread may expose:

- usernames/UUIDs
- chat
- coordinates
- inventories/equipment
- teams/parties/guilds
- private messages
- claims/ownership metadata

Default toward the minimum data required for the requested capability.

## Permission model questions

A future permission model may need categories such as:

- local player state
- nearby public world state
- other-player state
- container/block-entity state
- server diagnostics
- administrative state
- write/action permissions

Do not add these categories to V1 until real multiplayer requirements validate them.

## Transport and authentication

Loopback MCP remains useful for a local player, but dedicated/server modes may require:

- authenticated clients
- encrypted remote transport
- explicit server configuration
- audit logs
- rate limits per caller/player
- revocation/session expiry

Remote exposure should never be inherited accidentally from the V1 local MCP assumptions.

## Likely implementation principle

The eventual multiplayer design should make the server capable of narrowing or denying capabilities. Client-side Thread should never infer that an MCP tool is permitted simply because the tool exists locally.

The V1 abstraction should leave room for this, but V1 should not prebuild it.
