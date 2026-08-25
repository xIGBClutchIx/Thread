# Thread Roadmap

This roadmap is directional. Only V1 is committed scope.

## V1: Live Minecraft context over MCP

Theme: prove the architecture.

- Fabric
- read-only
- core tool/provider abstractions
- vanilla integration
- session-status/player/game/world/recipe tools
- MCP transport
- local-only defaults
- capability discovery
- testable without Minecraft for most core behavior

Exit condition: an MCP client can reliably answer useful questions from live Minecraft state.

## V1.x: Better vanilla context

Theme: deepen usefulness without changing the product model.

Possible work:

- biome/time/weather context
- advancements
- status effects
- better block inspection
- bounded nearby block entities
- selected/open container inspection
- recipe graph utilities
- "missing materials" computation
- more precise item metadata/components
- better search/ranking
- optional event/update subscriptions if MCP/client support makes them useful

## V2: Modpack awareness

Theme: understand the actual modpack rather than generic recipes.

The metadata-first integration framework already provides safe discovery and typed contribution
points. V2 makes it user-visible by shipping real third-party integrations.

Priority candidates:

1. EMI / JEI / REI recipe data
2. FTB Quests
3. storage systems such as Applied Energistics 2 / Refined Storage
4. Create
5. Mekanism

Examples:

- inspect active quests
- resolve modified recipes
- search network storage
- diagnose a machine/network state
- calculate progression dependencies

Avoid hard-coding every mod into core. Each integration should register capabilities/tools through the integration API.

## V3: Player-facing assistant experience

Theme: make Thread useful without leaving Minecraft.

Possible work:

- in-game assistant panel
- bring-your-own-model/provider configuration
- goal/pin system
- contextual suggestions
- recipe/progression step tracking
- optional MCP Apps or another UI surface if it materially helps

The in-game UI must consume the same Thread core tools rather than bypassing them.

## V4: Server and support tooling

Theme: use Thread beyond one local player.

Possible work:

- dedicated-server mode
- permission-aware server tools
- opt-in remote access with real authentication
- server diagnostics
- modpack support/debug bundles
- admin/support-oriented context
- Discord/web integrations

This phase requires a security model beyond V1 loopback assumptions.

## V5: Controlled actions

Theme: optional agent actions, only after read-only behavior is mature.

Potential actions:

- pin a waypoint
- select/pin a quest
- open a relevant screen
- send a chat message

Higher-risk actions such as moving items, automatic crafting, commands, or world interaction would require explicit permissions, confirmations, multiplayer policy handling, and auditability.

Read-only Thread should remain a valid mode permanently.

## Ideas intentionally parked

These are interesting but should not distract V1:

- automatic modpack walkthrough generation
- persistent semantic memory of a world
- multiplayer shared assistants
- voice assistant
- screenshot/vision analysis
- world build planning
- automated gameplay
- agent-driven testing of mods/modpacks
- support diagnostics for hosted servers
- remote observability dashboards

The architecture should leave room for these without prebuilding them.
