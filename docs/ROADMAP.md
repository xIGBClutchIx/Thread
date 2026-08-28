# Thread Roadmap

The implemented 1.0 scope is authoritative. Items below are possible directions, not commitments.

## Current 1.0 hardening

Keep the existing product reliable before broadening it:

- validate real MCP clients and clean-install release artifacts;
- improve diagnostics without logging player/world payloads;
- refine bounded vanilla context only where a concrete player question needs it;
- keep the integration API small while the first external adapter is built;
- preserve single-player, read-only, loopback, and logical-thread guarantees.

## External Thread Integrations

The next product expansion should be one separately distributed integration that proves the public
contract against a real unmet need. Candidate domains include recipe viewers, quests, storage
networks, or machinery, but the choice should follow a concrete use case and maintainability test.

Each adapter must version independently, keep target-mod APIs outside base Thread, return detached
bounded DTOs, and preserve native fallback behavior. Do not add an integration to the base JAR.

## Deferred after 1.0

Minecraft 1.21.1 and older, multiplayer gameplay access, write/action tools, and the first
separately distributed Thread Integrations packages all require their own scoped designs and proof
work. None is part of the 1.0 release.

## Better player context

Keep `minecraft.get_player` focused after its 1.0 enrichment. Add another tool only when it answers a
distinct question better than the existing twenty-one tools; avoid large background snapshots or generic
world scanning. Prefer focused, conditional metadata over raw attributes, NBT, or full entity dumps.

## Future product phases

These require separate designs before implementation:

- an in-game assistant UI consuming the same core tools;
- dedicated-server or multiplayer support with permissions, privacy, and anti-cheat policy;
- authenticated remote access;
- controlled actions with explicit scopes, confirmation, and auditability.

Read-only local Thread should remain a supported mode even if action-capable products are added.

## Parked ideas

Voice, persistent semantic world memory, automated gameplay, global build planning, remote
observability, and Quilt remain parked. The current architecture should not block them, but 1.0
should not prebuild frameworks for speculative work.
